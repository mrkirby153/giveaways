import {default as ax} from 'axios';
import {JWT_KEY} from "./constants";

export const axios = ax;

let initialized = false;
if (!initialized) {
  console.log("Initializing axios")
  if (localStorage.getItem(JWT_KEY)) {
    axios.defaults.headers.common['Authorization'] = 'Bearer ' + localStorage.getItem(JWT_KEY)
  }
  initialized = true;
}