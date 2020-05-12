import React from 'react';
import {GiveawayState} from "../../types";
import moment from "moment";
import './giveaway.scss';

interface Props {
  id: number,
  name: string,
  channelId: string,
  channelName: string,
  endsAt: string,
  entered: boolean,
  state: GiveawayState
}

const Giveaway: React.FC<Props> = (props) => {
  let ended = moment(props.endsAt).isBefore(moment())
  return (
      <div className="row">
        <div className="col-12 mb-2">
          <div className="card">
            <div className={"card-body giveaway " + (ended ? 'ended' : '')}>
              <span className="giveaway-name">{props.name}</span>
              <span className="channel">In #{props.channelName}</span>
              <span
                  className={props.entered ? 'entered' : 'not-entered'}>{props.entered ? 'Entered' : 'Not Entered'}</span>
              <span
                  className="ends-at">{ended ? 'Ended ' : 'Ends '} {moment(props.endsAt).fromNow()}</span>
            </div>
          </div>
        </div>
      </div>
  )
}

export default Giveaway;