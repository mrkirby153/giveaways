import Stomp, {Frame} from 'stompjs';
import {useContext, useEffect, useRef} from 'react';
import {WebsocketInformationContext} from "./App";
import SockJS from "sockjs-client";
import {Nullable, WebsocketMessage, WebsocketSubscription} from "./types";

let websocket: Nullable<Stomp.Client> = null;
let messageQueue: WebsocketMessage[] = []
let pendingSubscriptions: WebsocketSubscription[] = [];

export function useWebsocket() {
  const wsInformation = useContext(WebsocketInformationContext);
  // Connect to the websocket if we're not already connected
  useEffect(() => {
    console.log("ws", websocket);
    if (!wsInformation || !wsInformation.enabled) {
      return
    }
    if (websocket == null) {
      console.log("Opening new websocket connection")
      let client = Stomp.over(new SockJS(wsInformation.url));
      console.log("Setting ws", client);
      websocket = client;
      client.connect({}, () => {
        console.log("Connected! Subscribing to topics and sending queued messages", pendingSubscriptions, messageQueue);
        pendingSubscriptions.forEach(topic => {
          let id = client.subscribe(topic.topic, topic.callback).id;
          topic.pending(id);
        })
        pendingSubscriptions = [];
        messageQueue.forEach(msg => {
          client.send(msg.topic, msg.message)
        })
        messageQueue = [];
      })
    }
  }, [wsInformation])

  const subscribe = (topic: string, callback: (message: Frame) => any): Promise<string> => {
    if (!wsInformation || !wsInformation.enabled) {
      return Promise.resolve("");
    }
    if (websocket && websocket.connected) {
      return Promise.resolve(websocket.subscribe(topic, callback).id);
    } else {
      console.log("Deferring websocket subscription until after connection");
      return new Promise(resolve => {
        console.log("promise is being run")
        pendingSubscriptions.push({
          topic, callback, pending: resolve
        })
      });
    }
  }

  const unsubscribe = (id: string) => {
    if (!wsInformation || !wsInformation.enabled) {
      return;
    }
    if (websocket && websocket.connected) {
      websocket.unsubscribe(id);
    } else {
      console.error("Cannot unsubscribe from a disconnected websocket!")
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

export function useWebsocketTopic(topic: string, callback: (message: Frame) => any) {
  let {subscribe, unsubscribe} = useWebsocket();
  let subId = useRef("");
  useEffect(() => {
    console.log("useWebsocketTopic subscribing to " + topic);
    subscribe(topic, callback).then(id => subId.current = id);
    return () => {
      if (subId.current) {
        unsubscribe(subId.current);
      }
    }
  }, [topic]);
}