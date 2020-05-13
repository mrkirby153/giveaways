import React, {useContext} from 'react';
import {UserContext} from "../../App";

const Home: React.FC = () => {

  const user = useContext(UserContext)

  return (<React.Fragment>
    <h2 className="text-center mt-2">Discord Giveaways</h2>
    <p className="text-center">
      Discord Giveaways is a battle tested, highly scalable giveaway bot capable of handling giveaways
      with tens of thousands of entrants.
    </p>
    <p className="text-center">
      This bot is currently private, although this may change in the future
    </p>
  </React.Fragment>)
}
export default Home