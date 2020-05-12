import React from 'react';
import {BrowserRouter, Route, Switch} from "react-router-dom";
import routes from './routes';

function App() {

  const routeComponents = routes.map(route => {
    return <Route {...route}/>
  })

  return (
      <div className="app">
        <BrowserRouter>
          <Switch>
            {routeComponents}
          </Switch>
        </BrowserRouter>
      </div>
  );
}

export default App;
