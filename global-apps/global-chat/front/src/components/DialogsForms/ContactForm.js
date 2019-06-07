import React from "react";
import {withStyles} from '@material-ui/core';
import formStyles from "./formStyles";
import Button from '@material-ui/core/Button';
import Dialog from '../Dialog/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import TextField from '@material-ui/core/TextField';
import connectService from "../../common/connectService";
import ContactsContext from "../../modules/contacts/ContactsContext";
import * as PropTypes from "prop-types";
import { withSnackbar } from 'notistack';
import ButtonWithProgress from "../UIElements/ButtonProgress";

class ContactForm extends React.Component {
  state = {
    pubKey: '',
    name: '',
    loading: false,
  };

  handlePKChange = (event) => {
    this.setState({pubKey: event.target.value});
  };

  handleNameChange = (event) => {
    this.setState({name: event.target.value});
  };

  onSubmit = (event) => {
    event.preventDefault();

    this.setState({
      loading: true
    });

    return this.props.addContact(this.state.pubKey, this.state.name)
      .then(() => {
        this.props.onClose();
      })
      .catch((err) => {
        this.props.enqueueSnackbar(err.message, {
          variant: 'error'
        });
      })
      .finally(() => {
        this.setState({
          loading: false
        });
      });
  };

  render() {
    return (
      <Dialog
        open={this.props.open}
        onClose={this.props.onClose}
        aria-labelledby="form-dialog-title"
      >
        <form onSubmit={this.onSubmit}>
          <DialogTitle id="customized-dialog-title" onClose={this.props.onClose}>Add Contact</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Enter new contact to start chat
            </DialogContentText>
            <TextField
              required={true}
              autoFocus
              disabled={this.state.loading}
              margin="normal"
              label="Key"
              type="text"
              fullWidth
              variant="outlined"
              onChange={this.handlePKChange}
            />
            <TextField
              required={true}
              autoFocus
              disabled={this.state.loading}
              margin="normal"
              label="Name"
              type="text"
              fullWidth
              variant="outlined"
              onChange={this.handleNameChange}
            />
          </DialogContent>
          <DialogActions>
            <Button
              disabled={this.state.loading}
              onClick={this.props.onClose}
            >
              Cancel
            </Button>
            <ButtonWithProgress
              loading={this.state.loading}
              type={"submit"}
              color={"primary"}
              variant={"contained"}
            >
              Add Contact
            </ButtonWithProgress>
          </DialogActions>
        </form>
      </Dialog>
    );
  }
}

ContactForm.propTypes = {
  enqueueSnackbar: PropTypes.func.isRequired,
};

export default connectService(
  ContactsContext, (state, contactsService) => ({
    addContact(pubKey, name) {
      return contactsService.addContact(pubKey, name);
    }
  })
)(
  withSnackbar(withStyles(formStyles)(ContactForm))
);
