import Stomp, {Frame} from 'stompjs';
import {useContext, useEffect, useRef} from 'react';
import {WebsocketInformationContext} from "./App";
import SockJS from "sockjs-client";
import {Nullable, WebsocketMessage, WebsocketSubscription} from "./types";
import {JWT_KEY} from "./constants";
import ld_remove from 'lodash/remove';

let websocket: Nullable<Stomp.Client> = null;
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
      let client = Stomp.over(new SockJS(wsInformation.url));
      console.log("Setting ws", client);
      websocket = client;
      client.connect({
        passcode: localStorage.getItem(JWT_KEY)
      }, (frame) => {
        console.log("WS URL:", client.ws.url);
        console.log("Connected! Subscribing to topics and sending queued messages", pendingSubscriptions, messageQueue);
        console.groupCollapsed("Pending Subscriptions")
        pendingSubscriptions.forEach(topic => {
          let id = client.subscribe(topic.topic, topic.callback).id;
          console.debug(`Subscribed to ${topic.topic}: ${topic.id} => ${id}`)
          subMap.set(topic.id, id);
        })
        console.groupEnd();
        pendingSubscriptions = [];
        console.groupCollapsed("Pending Messages")
        messageQueue.forEach(msg => {
          console.debug("Sending queued message", msg)
          client.send(msg.topic, msg.message)
        })
        console.groupEnd();
        messageQueue = [];
      })
    }
  }, [wsInformation])

  const subscribe = (topic: string, callback: (message: Frame) => any): number => {
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

  const send = (topic: string, data: any = null) => {
    if (!wsInformation || !wsInformation.enabled) {
      return;
    }
    if (websocket && websocket.connected) {
      websocket.send(topic, {}, data);
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

export function useWebsocketTopic(topic: string, callback: (message: Frame) => any, effects: any[] = []) {
  let {subscribe, unsubscribe} = useWebsocket();
  let subId = useRef(-1);
  useEffect(() => {
    subId.current = subscribe(topic, callback);
    return () => {
      console.debug("Unsubscribing", subId.current);
      unsubscribe(subId.current);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topic, ...effects]);
}