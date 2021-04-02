import {Client, IFrame} from '@stomp/stompjs';
import {useContext, useEffect, useRef} from 'react';
import {WebsocketInformationContext} from "./App";
import {Nullable, WebsocketMessage, WebsocketSubscription} from "./types";
import {JWT_KEY} from "./constants";
import ld_remove from 'lodash/remove';
import SockJS from "sockjs-client";

let websocket: Nullable<Client> = null;
let messageQueue: WebsocketMessage[] = []
let pendingSubscriptions: WebsocketSubscription[] = [];

let nextId = 0;
let subMap = new Map<number, string>();

export function useWebsocket() {
  const wsInformation = useContext(WebsocketInformationContext);
  // Connect to the websocket if we're not already connected
  useEffect(() => {
    if (!wsInformation || !wsInformation.enabled) {
      return
    }
    if (websocket == null) {
      console.log("Opening new websocket connection")
      let client = new Client({
        connectHeaders: {
          passcode: localStorage.getItem(JWT_KEY) as string
        },
        debug: function (str) {
          if (process.env.NODE_ENV !== "production") {
            console.log(str)
          }
        },
        webSocketFactory: function() {
          return new SockJS(wsInformation.url);
        }
      });
      websocket = client;
      client.onConnect = function (frame) {
        console.log("Connected! Subscribing to topics and sending queued messages", pendingSubscriptions, messageQueue);
        console.groupCollapsed("Pending Subscriptions")
        pendingSubscriptions.forEach(topic => {
          let id = client.subscribe(topic.topic, topic.callback).id
          console.debug(`Subscribed to ${topic.topic}: ${topic.id} => ${id}`)
          subMap.set(topic.id, id)
        })
        pendingSubscriptions = [];
        console.groupEnd();
        console.groupCollapsed("Pending Messages")
        messageQueue.forEach(msg => {
          console.log("Sending queued message", msg);
          client.publish({
            destination: msg.topic,
            body: msg.message
          })
        })
        console.groupEnd();
        messageQueue = [];
      }
      client.activate();
    }
  }, [wsInformation])

  const subscribe = (topic: string, callback: (message: IFrame) => any): number => {
    if (!wsInformation || !wsInformation.enabled) {
      return -1;
    }
    let subId = nextId++;
    if (websocket && websocket.connected) {
      let subscription = websocket.subscribe(topic, callback)
      subMap.set(subId, subscription.id);
      console.debug(`Subscribing to ${topic} with subId: ${subId}`)
    } else {
      console.log(`Deferring subscription to ${topic} with subId: ${subId}`)
      pendingSubscriptions.push({
        topic, callback, id: subId
      })
    }
    return subId;
  }

  const unsubscribe = (id: number) => {
    if (!wsInformation || !wsInformation.enabled) {
      return;
    }
    if (websocket && websocket.connected) {
      let subId = subMap.get(id)
      if (!subId) {
        console.warn("No subscription with id ", id);
        return;
      }
      console.debug(`Unsubscribing ${id} => ${subId}`)
      websocket.unsubscribe(subId);
    } else {
      ld_remove(pendingSubscriptions, {
        id
      })
    }
  }

  const send = (topic: string, data: any = null, headers: any = null) => {
    if (!wsInformation || !wsInformation.enabled) {
      return;
    }
    if (websocket && websocket.connected) {
      websocket.publish({
        destination: topic,
        body: data,
        headers
      })
    } else {
      console.log("Deferring message until connected ", topic, data);
      messageQueue.push({
        topic,
        message: data
      })
    }
  }
  return {send, subscribe, unsubscribe}
}

export function useWebsocketTopic(topic: string, callback: (message: IFrame) => any, effects: any[] = [], enabled: boolean = true) {
  let {subscribe, unsubscribe} = useWebsocket();
  let subId = useRef(-1);
  useEffect(() => {
    if (!enabled) {
      return;
    }
    subId.current = subscribe(topic, callback);
    return () => {
      console.debug("Unsubscribing", subId.current);
      unsubscribe(subId.current);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topic, enabled, ...effects]);
}