package engine

import (
	"context"
	"fmt"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

func (c *Client) SendTextMessage(chatJid string, text string) (string, error) {
	return c.sendTextInternal(chatJid, text, "", "")
}

func (c *Client) SendReplyMessage(chatJid, text, quotedStanzaID, quotedSenderJid, quotedText string) (string, error) {
	return c.sendTextInternal(chatJid, text, quotedStanzaID, quotedSenderJid, quotedText)
}

func (c *Client) sendTextInternal(chatJid, text, quotedStanzaID, quotedSenderJid string, quotedTextArgs ...string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}

	var msg *waE2E.Message
	if quotedStanzaID != "" {
		quotedText := ""
		if len(quotedTextArgs) > 0 {
			quotedText = quotedTextArgs[0]
		}
		participant, err := types.ParseJID(quotedSenderJid)
		if err != nil {
			return "", err
		}
		msg = &waE2E.Message{
			ExtendedTextMessage: &waE2E.ExtendedTextMessage{
				Text: proto.String(text),
				ContextInfo: &waE2E.ContextInfo{
					StanzaID:    proto.String(quotedStanzaID),
					Participant: proto.String(participant.ToNonAD().String()),
					QuotedMessage: &waE2E.Message{
						Conversation: proto.String(quotedText),
					},
				},
			},
		}
	} else {
		msg = &waE2E.Message{Conversation: proto.String(text)}
	}

	resp, err := c.inner.SendMessage(context.Background(), jid, msg)
	if err != nil {
		c.log.Errorf("send failed to %s: %v", chatJid, err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", resp.ID, resp.Timestamp.Unix()), nil
}