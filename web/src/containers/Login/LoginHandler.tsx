import React, {useEffect} from 'react';
import axios from 'axios';
import {JWT_KEY, WINDOW_MSG_AUTHENTICATED} from "../../constants";

const LoginHandler: React.FC = () => {

  const onLoad = () => {
    const redirectUri = `${window.location.protocol}//${window.location.host}${window.location.pathname}`
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has("code")) {
      const code = urlParams.get("code");
      console.log(`Received ${code} from Discord`)
      axios.post('/api/login', {
        code,
        redirect_uri: redirectUri
      }).then(resp => {
        localStorage.setItem(JWT_KEY, resp.data);
        window.opener.postMessage(WINDOW_MSG_AUTHENTICATED)
        window.close();
      })
    } else {
      axios.get('/api/client').then(resp => {
        const clientId = resp.data;
        const authUrl = `https://discord.com/api/oauth2/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=identify`
        console.log(`Redirecting to ${authUrl}`)
        window.location.replace(authUrl);
      })
    }
  };

  useEffect(onLoad, [])

  return <h1>Please wait...</h1>
};

export default LoginHandler;