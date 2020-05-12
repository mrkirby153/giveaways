import React, {useEffect, useState} from 'react';
import {RouteComponentProps} from 'react-router-dom';
import LoginButton from "../Login/LoginButton";
import {axios} from "../../utils";
import {Giveaway as GiveawayType} from "../../types";
import Giveaway from "./Giveaway";

interface MatchProps {
  server: string
}

type MyProps = RouteComponentProps<MatchProps>

const Giveaways: React.FC<MyProps> = (props) => {

  const serverId = props.match.params.server;

  const [giveaways, setGiveaways] = useState<GiveawayType[]>([])


  const getGiveaways = () => {
    axios.get(`/api/giveaways/${serverId}`).then(resp => {
      setGiveaways(resp.data);
    })
  }

  useEffect(getGiveaways, []);

  const giveawayElements = giveaways.map(giveaway => {
    return <Giveaway key={giveaway.id}/>
  });

  return (
      <React.Fragment>
        <LoginButton/>
        <h1>Giveaways</h1>
        Server: {props.match.params.server}
        {giveawayElements}
      </React.Fragment>
  )
}

export default Giveaways