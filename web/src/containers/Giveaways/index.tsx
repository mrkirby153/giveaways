import React, {useEffect, useState} from 'react';
import {RouteComponentProps} from 'react-router-dom';
import {axios, loggedIn} from "../../utils";
import {Giveaway as GiveawayType, Giveaways as GiveawaysType, Guild} from "../../types";
import Giveaway from "./Giveaway";
import moment from 'moment';
import './index.scss';
import LoginButton from "../Login/LoginButton";
import ld_orderBy from 'lodash/orderBy';

interface MatchProps {
  server: string
}

type MyProps = RouteComponentProps<MatchProps>

const Giveaways: React.FC<MyProps> = (props) => {

  const serverId = props.match.params.server;

  const [giveaways, setGiveaways] = useState<GiveawaysType>({active: [], inactive: []})
  const [guild, setGuild] = useState<Guild>({
    id: '',
    name: 'Loading',
    icon: null
  })


  const getGiveaways = () => {
    if (loggedIn())
      axios.get(`/api/giveaways/${serverId}`).then(resp => {
        setGiveaways(resp.data);
      })
  }
  const getGuild = () => {
    axios.get(`/api/server/${serverId}`).then(resp => {
      setGuild(resp.data);
    })
  }

  useEffect(getGiveaways, []);
  useEffect(getGuild, []);

  const activeGiveawayElements = giveaways.active.map(giveaway => {
    return <Giveaway key={giveaway.id} {...giveaway}/>
  });


  const endedGiveawayElements = ld_orderBy(giveaways.inactive, (o: GiveawayType) => {
    return moment.utc(o.endsAt)
  }, ['desc']).map(giveaway => {
    return <Giveaway key={giveaway.id} {...giveaway}/>
  });

  return (
      <React.Fragment>
        <div className="container-fluid">
          <div className="row">
            <div className="col-6 offset-3">
              <h1 className="text-center">{guild.name} Giveaways</h1>
              {guild.icon &&
              <img src={`https://cdn.discordapp.com/icons/${serverId}/${guild.icon}.png`}
                   className="guild-icon mb-2" alt={guild.name + " icon"}/>}
              {!loggedIn() && <p>You need to log in before you can view the giveaway list</p>}
              <div className="mb-2">
                <LoginButton/>
              </div>
              {loggedIn() && <React.Fragment>
                <div className="container-fluid">
                  <h2>Active Giveaways</h2>
                  {activeGiveawayElements}
                </div>
                <hr/>
                <div className="container-fluid">
                  <h2>Past Giveaways</h2>
                  {endedGiveawayElements}
                </div>
              </React.Fragment>}
            </div>
          </div>
        </div>

      </React.Fragment>
  )
}

export default Giveaways