import {RouteProps as rp} from 'react-router-dom';
import LoginHandler from "./containers/Login/LoginHandler";

interface RouteProps extends rp {
  key: string
}

const routes: RouteProps[] = [
  {
    key: 'login-callback',
    path: '/login',
    exact: true,
    component: LoginHandler
  }
];

export default routes;