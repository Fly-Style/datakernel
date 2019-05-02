import ChatMessage from "./ChatMessage";

class ChatOTOperation {
  constructor(message, remove) {
    this.message = message;
    this.remove = remove;
  }

  static EMPTY = new ChatOTOperation(new ChatMessage(0, '', ''), false);

  apply(state) {
    const key = JSON.stringify({
      message: this.message,
      remove: this.remove
    });

    if (this.remove) {
      state.delete(key);
    } else {
      state.add(key);
    }

    return state;
  }

  isEmpty() {
    return this.message.isEmpty();
  }

  invert() {
    return new ChatOTOperation(this.message, !this.remove);
  }

  isEqual(chatOTOperation) {
    return chatOTOperation.message.isEqual(this.message) && chatOTOperation.remove === this.remove;
  }
}

export default ChatOTOperation;
