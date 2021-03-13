import React, {useContext, useEffect, useState} from 'react';
import {RouteComponentProps} from 'react-router-dom';
import {axios, loggedIn} from "../../utils";
import {
  Giveaway as GiveawayType,
  Giveaways as GiveawaysType,
  GiveawayState,
  Guild
} from "../../types";
import Giveaway from "./Giveaway";
import moment from 'moment';
import './index.scss';
import LoginButton from "../Login/LoginButton";
import ld_orderBy from 'lodash/orderBy';
import ld_find from 'lodash/find';
import ld_filter from 'lodash/filter';
import ld_findIndex from 'lodash/findIndex';
import ld_cloneDeep from 'lodash/cloneDeep';
import {Frame} from "stompjs";
import {useWebsocketTopic} from "../../hooks";
import {UserContext} from "../../App";

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

  const handleGiveawayStateChange = (message: Frame) => {
    let data = JSON.parse(message.body);
    switch (data.state) {
      case "START":
        console.log("New giveaway: ", data)
        setGiveaways(g => {
          let active = [...g.active];
          active.push(data.giveaway);
          return {
            active,
            inactive: g.inactive
          };
        });
        break;
      case "END":
        console.log("Giveaway ended: ", data);
        setGiveaways(g => {
          let giveawayId = data.giveaway.id;
          let target = ld_find(g.active, {id: giveawayId});
          if (target) {
            let newActive = ld_filter(g.active, (g) => g.id !== giveawayId);
            let newInactive = [...g.inactive];
            target.state = GiveawayState.ENDED;
            target.endsAt = data.giveaway.endsAt;
            newInactive.push(target);
            return {
              active: newActive,
              inactive: newInactive
            }
          }
          return g;
        })
    }
  }

  const handleGiveawayEnter = (frame: Frame) => {
    let data = JSON.parse(frame.body);
    setGiveaways(g => {
      let target = ld_findIndex(g.active, {id: data.giveaway.id});
      if (target == -1) {
        return g; // Giveaway not found, no longer active, or from a different server
      }
      let newGiveaways = ld_cloneDeep(g);
      newGiveaways.active[target].entered = true;
      return newGiveaways;
    })
  }
  useWebsocketTopic(`/topic/${serverId}/giveaway`, handleGiveawayStateChange)
  useWebsocketTopic(`/user/queue/giveaway/${serverId}/user`, handleGiveawayEnter)


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