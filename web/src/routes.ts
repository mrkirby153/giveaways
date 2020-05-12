import {RouteProps as rp} from 'react-router-dom';
import LoginHandler from "./containers/Login/LoginHandler";
import Home from "./containers/Home";
import Giveaways from "./containers/Giveaways";

interface RouteProps extends rp {
  key: string
}

const routes: RouteProps[] = [
  {
    key: 'home',
    path: '/',
    exact: true,
    component: Home
  },
  {
    key: 'login-callback',
    path: '/login',
    exact: true,
    component: LoginHandler
  },
  {
    key: 'giveaways',
    path: '/giveaways/:server',
    exact: true,
    component: Giveaways
  }
];

export default routes;