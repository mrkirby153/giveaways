import React from 'react';
import {Link} from "react-router-dom";

const Home: React.FC = () => {
  return (<React.Fragment>
    <h2 className="text-center mt-2">Discord Giveaways</h2>
    <p className="text-center">
      Discord Giveaways is a battle tested, highly scalable giveaway bot capable of handling
      giveaways
      with tens of thousands of entrants.
    </p>
    <p className="text-center">
      <Link className="btn btn-primary btn-lg" to="/invite">Add Giveaways to your Server</Link>
    </p>
  </React.Fragment>)
}
export default Home