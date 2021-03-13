import React, {useEffect, useState} from 'react';
import {BrowserRouter, Route, Switch} from "react-router-dom";
import routes from './routes';

import {axios} from "./utils";
import {JWT_KEY} from "./constants";
import {Nullable, User, WebsocketInformation} from "./types";

export const UserContext = React.createContext<User | null>(null)
UserContext.displayName = 'UserContext'

export const WebsocketInformationContext = React.createContext<Nullable<WebsocketInformation>>(null);
WebsocketInformationContext.displayName = 'WsInformation'

function App() {

  const [user, setUser] = useState<User | null>(null);
  const [wsInformation, setWsInformation] = useState<Nullable<WebsocketInformation>>(null);

  const getUser = () => {
    if (localStorage.getItem(JWT_KEY)) {
      axios.get('/api/user').then(resp => {
        setUser(resp.data);
      }).catch(e => {
        localStorage.removeItem(JWT_KEY)
        window.location.reload();
      })
    }
  }

  // Defer mounting until this is done
  const getWebsocketUrl = () => {
    axios.get('/api/ws').then(resp => {
      setWsInformation({
        enabled: resp.data.enabled,
        url: resp.data.wsUrl,
        client: null
      })
    }).catch(e => {
      console.log("Could not retrieve websocket information")
      setWsInformation({
        enabled: false,
        url: "",
        client: null
      })
    })
  }

  useEffect(getUser, []);
  useEffect(getWebsocketUrl, []);


  const routeComponents = routes.map(route => {
    return <Route {...route}/>
  })

  return (
      <div className="app">
        {wsInformation && <WebsocketInformationContext.Provider value={wsInformation}>
          <UserContext.Provider value={user}>
            <BrowserRouter>
              <Switch>
                {routeComponents}
              </Switch>
            </BrowserRouter>
          </UserContext.Provider>
        </WebsocketInformationContext.Provider>}
      </div>
  );
}

export default App;
