package engine

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

var statusBroadcastJID = types.NewJID("status", types.BroadcastServer)

func (c *Client) SendTextStatus(text string, backgroundColor int64) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	msg := &waE2E.Message{
		ExtendedTextMessage: &waE2E.ExtendedTextMessage{
			Text:           proto.String(text),
			BackgroundArgb: proto.Uint32(uint32(backgroundColor)),
		},
	}
	resp, err := c.inner.SendMessage(context.Background(), statusBroadcastJID, msg)
	if err != nil {
		c.log.Errorf("send text status failed: %v", err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", resp.ID, resp.Timestamp.Unix()), nil
}

func (c *Client) SendImageStatus(filePath, caption string) (string, error) {
	return c.sendMediaStatus(filePath, caption, whatsmeow.MediaImage)
}

func (c *Client) SendVideoStatus(filePath, caption string) (string, error) {
	return c.sendMediaStatus(filePath, caption, whatsmeow.MediaVideo)
}

func (c *Client) sendMediaStatus(filePath, caption string, mediaType whatsmeow.MediaType) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	data, err := os.ReadFile(filePath)
	if err != nil {
		return "", err
	}
	uploaded, err := c.inner.Upload(context.Background(), data, mediaType)
	if err != nil {
		return "", err
	}
	mimetype := http.DetectContentType(data)

	var msg *waE2E.Message
	switch mediaType {
	case whatsmeow.MediaImage:
		msg = &waE2E.Message{
			ImageMessage: &waE2E.ImageMessage{
				URL:           proto.String(uploaded.URL),
				DirectPath:    proto.String(uploaded.DirectPath),
				MediaKey:      uploaded.MediaKey,
				Mimetype:      proto.String(mimetype),
				FileEncSHA256: uploaded.FileEncSHA256,
				FileSHA256:    uploaded.FileSHA256,
				FileLength:    proto.Uint64(uint64(len(data))),
				Caption:       proto.String(caption),
			},
		}
	case whatsmeow.MediaVideo:
		msg = &waE2E.Message{
			VideoMessage: &waE2E.VideoMessage{
				URL:           proto.String(uploaded.URL),
				DirectPath:    proto.String(uploaded.DirectPath),
				MediaKey:      uploaded.MediaKey,
				Mimetype:      proto.String(mimetype),
				FileEncSHA256: uploaded.FileEncSHA256,
				FileSHA256:    uploaded.FileSHA256,
				FileLength:    proto.Uint64(uint64(len(data))),
				Caption:       proto.String(caption),
			},
		}
	default:
		return "", fmt.Errorf("unsupported status media type")
	}

	resp, err := c.inner.SendMessage(context.Background(), statusBroadcastJID, msg)
	if err != nil {
		c.log.Errorf("send media status failed: %v", err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", resp.ID, resp.Timestamp.Unix()), nil
}

func (c *Client) MarkStatusViewed(senderJid string, messageIDsCSV string) error {
	sender, err := types.ParseJID(senderJid)
	if err != nil {
		return err
	}
	var raw []string
	for _, s := range strings.Split(messageIDsCSV, ",") {
		s = strings.TrimSpace(s)
		if s != "" {
			raw = append(raw, s)
		}
	}
	ids := make([]types.MessageID, 0, len(raw))
	for _, id := range raw {
		ids = append(ids, types.MessageID(id))
	}
	return c.inner.MarkRead(
		context.Background(),
		ids,
		time.Now(),
		statusBroadcastJID,
		sender.ToNonAD(),
		types.ReceiptTypeRead,
	)
}

func (c *Client) RevokeStatus(messageID string) error {
    if c.inner.Store.ID == nil {
        return ErrNotLoggedInLocal
    }

    ownJID := c.inner.Store.ID.ToNonAD()

    revokeMsg := c.inner.BuildRevoke(
        types.StatusBroadcastJID,
        ownJID,
        types.MessageID(messageID),
    )

    resp, err := c.inner.SendMessage(
        context.Background(),
        types.StatusBroadcastJID,
        revokeMsg,
        whatsmeow.SendRequestExtra{Peer: true},
    )
    if err != nil {
        c.log.Errorf("revoke status %s failed: %v", messageID, err)
        return err
    }

    c.log.Infof("revoke status %s sent, response ID: %s", messageID, resp.ID)
    return nil
}
