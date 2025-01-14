import React from 'react';
import {withStyles} from '@material-ui/core';
import PropTypes from 'prop-types';
import chatStyles from './chatRoomStyles';
import Messages from './Messages/Messages';
import MessageForm from './MessageForm/MessageForm';
import ChatRoomService from "../../../modules/chatroom/ChatRoomService";
import ChatRoomContext from '../../../modules/chatroom/ChatRoomContext';
import connectService from '../../../common/connectService';
import AccountContext from '../../../modules/account/AccountContext';

class ChatRoom extends React.Component {
  static propTypes = {
    roomId: PropTypes.string.isRequired
  };

  state = {
    roomId: null,
    chatRoomService: null
  };

  static getDerivedStateFromProps(props, state) {
    if (props.roomId !== state.roomId) {
      if (state.chatRoomService) {
        state.chatRoomService.stop();
      }

      const chatRoomService = ChatRoomService.createFrom(props.roomId, props.publicKey, props.isNew);
      chatRoomService.init();

      return {
        roomId: props.roomId,
        chatRoomService
      };
    }
  }

  componentWillUnmount() {
    this.state.chatRoomService.stop();
  }

  update = newState => this.setState(newState);

  render() {
    const {classes, roomId} = this.props;
    return (
      <ChatRoomContext.Provider value={this.state.chatRoomService}>
        <div className={classes.root}>
          <div className={classes.headerPadding}/>
          <Messages roomId={roomId}/>
          <MessageForm/>
        </div>
      </ChatRoomContext.Provider>
    );
  }
}

export default connectService(AccountContext, ({publicKey}) => ({publicKey}))(
    withStyles(chatStyles)(ChatRoom)
);
