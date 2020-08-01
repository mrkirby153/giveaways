import React, {useEffect, useState} from 'react';
import axios from 'axios';
import {AUTH_STATE_KEY, JWT_KEY, PREV_URL_KEY, WINDOW_MSG_AUTHENTICATED} from "../../constants";

const LoginHandler: React.FC = () => {

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const getState = (): string => {
    const state = Math.random().toString(36);
    sessionStorage.setItem(AUTH_STATE_KEY, state);
    return state;
  }

  const isStateValid = (state: string): boolean => {
    const storedState = sessionStorage.getItem(AUTH_STATE_KEY);
    return storedState === state;
  }

  const onLoad = () => {
    const redirectUri = `${window.location.protocol}//${window.location.host}${window.location.pathname}`
    const urlParams = new URLSearchParams(window.location.search);
    if(urlParams.has("error")) {
      setErrorMessage(`Error: ${urlParams.get('error')}`)
      return;
    }
    if (urlParams.has("code")) {
      const code = urlParams.get("code");
      const state = urlParams.get("state");
      if (state == null || !isStateValid(state)) {
        setErrorMessage("Invalid state. Please try again")
        return;
      }
      axios.post('/api/login', {
        code,
        redirect_uri: redirectUri
      }).then(resp => {
        localStorage.setItem(JWT_KEY, resp.data);
        window.opener.postMessage(WINDOW_MSG_AUTHENTICATED)
        setSuccess(true);
        sessionStorage.removeItem(AUTH_STATE_KEY);
        window.close();
      }).catch(ex => {
        setErrorMessage(`An error occurred logging you in: ${ex.response.data.message}`);
        sessionStorage.removeItem(AUTH_STATE_KEY);
      })
    } else {
      axios.get('/api/client').then(resp => {
        const clientId = resp.data;
        const state = getState();
        const authUrl = `https://discord.com/api/oauth2/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=identify&prompt=none&state=${state}`
        console.log(`Redirecting to ${authUrl}`)
        window.location.replace(authUrl);
      }).catch(ex => {
        console.log(ex)
        setErrorMessage(`Could not redirect`);
      })
    }
  }

  const redirectToPreviousWindow = () => {
    const redirect = sessionStorage.getItem(PREV_URL_KEY);
    if (redirect) {
      sessionStorage.removeItem(PREV_URL_KEY);
      window.location.replace(redirect)
    }
  }

  useEffect(onLoad, [])

  if (errorMessage) {
    return (<React.Fragment>
      <h1>Error</h1>
      <p>
        An error has occurred when attempting to log you in:
      </p>
      <pre>{errorMessage}</pre>
      <p>
        Please close this window and try again
      </p>
    </React.Fragment>)
  }

  if (success) {
    return (<React.Fragment>
      <h1>Success</h1>
      <p>
        You have been logged in.
      </p>
      <button className="btn btn-primary" onClick={redirectToPreviousWindow}>Click here if you are not automatically redirected</button>
    </React.Fragment>)
  }

  return <h1>Please wait...</h1>
};

export default LoginHandler;