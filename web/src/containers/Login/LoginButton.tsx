import React from 'react';
import {JWT_KEY, PREV_URL_KEY, WINDOW_MSG_AUTHENTICATED} from "../../constants";

const LoginButton: React.FC<React.ButtonHTMLAttributes<HTMLButtonElement>> = (props) => {

  const authToken = localStorage.getItem(JWT_KEY);

  const receivePostedMessage = (event: any) => {
    if (event.origin !== window.origin) {
      return;
    }
    if (event.data === WINDOW_MSG_AUTHENTICATED) {
      window.removeEventListener("message", receivePostedMessage);
      window.location.reload();
    }
  }

  const openPopupWindow = () => {

    localStorage.setItem(PREV_URL_KEY, window.location.href);
    window.open('/login', 'test', 'menubar=no,toolbar=no,location=no')
    window.addEventListener("message", receivePostedMessage, false);
  }

  const logOut = () => {
    localStorage.removeItem(JWT_KEY);
    window.location.reload();
  }

  if (authToken) {
    return (
        <button className="btn btn-primary" onClick={logOut} {...props}>Log Out</button>
    )
  } else {
    return <button className="btn btn-primary" onClick={openPopupWindow} {...props}>Log In</button>
  }

}
export default LoginButton