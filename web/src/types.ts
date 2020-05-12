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