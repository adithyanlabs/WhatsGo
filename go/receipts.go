package engine

import (
	"context"
	"strings"
	"time"

	"go.mau.fi/whatsmeow/types"
)

func (c *Client) MarkChatRead(chatJid string, messageIDsCSV string, senderJid string) error {
	if c.inner.Store.ID == nil {
		return ErrNotLoggedInLocal
	}
	chat, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	sender, err := types.ParseJID(senderJid)
	if err != nil {
		return err
	}

	rawIDs := strings.Split(messageIDsCSV, ",")
	ids := make([]types.MessageID, 0, len(rawIDs))
	for _, id := range rawIDs {
		trimmed := strings.TrimSpace(id)
		if trimmed != "" {
			ids = append(ids, types.MessageID(trimmed))
		}
	}

	return c.inner.MarkRead(context.Background(), ids, time.Now(), chat, sender)
}