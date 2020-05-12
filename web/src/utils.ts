import {default as ax} from 'axios';
import {JWT_KEY} from "./constants";

export const axios = ax;

let initialized = false;
if (!initialized) {
  if (localStorage.getItem(JWT_KEY)) {
    axios.defaults.headers.common['Authorization'] = 'Bearer ' + localStorage.getItem(JWT_KEY)
  }
  initialized = true;
}

export function loggedIn(): boolean {
  return localStorage.getItem(JWT_KEY) != null;
}