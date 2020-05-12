import React, {useContext} from 'react';
import LoginButton from "../Login/LoginButton";
import {UserContext} from "../../App";

const Home: React.FC = () => {

  const user = useContext(UserContext)

  return (<div>
    This is the home page
    <pre>{JSON.stringify(user)}</pre>
    <LoginButton/>
  </div>)
}
export default Home