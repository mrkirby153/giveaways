import React from 'react';
import {JWT_KEY, WINDOW_MSG_AUTHENTICATED} from "../../constants";

const LoginButton: React.FC = () => {

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
    window.open('/login', 'test', 'menubar=no,toolbar=no,location=no')
    window.addEventListener("message", receivePostedMessage, false);
  }

  const logOut = () => {
    localStorage.removeItem(JWT_KEY);
    window.location.reload();
  }

  if (authToken) {
    return (
        <button className="btn btn-primary" onClick={logOut}>Log Out</button>
    )
  } else {
    return <button className="btn btn-primary" onClick={openPopupWindow}>Log In</button>
  }

}
export default LoginButton