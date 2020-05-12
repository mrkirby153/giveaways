import React, {useEffect, useState} from 'react';
import {BrowserRouter, Route, Switch} from "react-router-dom";
import routes from './routes';

import {axios} from "./utils";
import {JWT_KEY} from "./constants";
import {User} from "./types";

export const UserContext = React.createContext<User | null>(null)
UserContext.displayName = 'UserContext'

function App() {

  const [user, setUser] = useState<User | null>(null);

  const getUser = () => {
    if (localStorage.getItem(JWT_KEY)) {
      axios.get('/api/user').then(resp => {
        setUser(resp.data);
      }).catch(e => {
        localStorage.removeItem(JWT_KEY)
      })
    }
  }

  useEffect(getUser, []);


  const routeComponents = routes.map(route => {
    return <Route {...route}/>
  })

  return (
      <div className="app">
        <UserContext.Provider value={user}>
          <BrowserRouter>
            <Switch>
              {routeComponents}
            </Switch>
          </BrowserRouter>
        </UserContext.Provider>
      </div>
  );
}

export default App;
