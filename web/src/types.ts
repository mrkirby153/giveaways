import {IFrame, Client} from "@stomp/stompjs";

export interface User {
  id: string,
  username: string,
  discriminator: string,
  avatar: string
}

export enum GiveawayState {
  RUNNING = "RUNNING", ENDED = "ENDED", ENDING = "ENDING"
}

export interface Giveaway {
  id: number,
  name: string,
  channelId: string,
  channelName: string,
  endsAt: string,
  entered: boolean,
  state: GiveawayState
}

export interface Guild {
  id: string,
  name: string,
  icon: string | null
}

export interface Giveaways {
  active: Giveaway[]
  inactive: Giveaway[]
}

export interface WebsocketInformation {
  url: string,
  enabled: boolean,
  client: Client | null
}

export interface WebsocketMessage {
  topic: string,
  message: any | null
}

export interface WebsocketSubscription {
  id: number,
  topic: string,
  callback: (message: IFrame) => any,
}

export type Nullable<T> = T | null;