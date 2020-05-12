import {RouteProps as rp} from 'react-router-dom';
import LoginHandler from "./containers/Login/LoginHandler";
import Home from "./containers/Home";

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
  }
];

export default routes;