package engine

import (
	"context"
	"fmt"
	"net/http"
	"os"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

func (c *Client) SendImageMessage(chatJid, filePath, caption string, thumbnail []byte) (string, error) {
	return c.sendMediaMessage(chatJid, filePath, caption, thumbnail, whatsmeow.MediaImage)
}

func (c *Client) SendVideoMessage(chatJid, filePath, caption string, thumbnail []byte) (string, error) {
	return c.sendMediaMessage(chatJid, filePath, caption, thumbnail, whatsmeow.MediaVideo)
}

func (c *Client) sendMediaMessage(chatJid, filePath, caption string, thumbnail []byte, mediaType whatsmeow.MediaType) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
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
				JPEGThumbnail: thumbnail,
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
				JPEGThumbnail: thumbnail,
			},
		}
	default:
		return "", fmt.Errorf("unsupported media type")
	}

	resp, err := c.inner.SendMessage(context.Background(), jid, msg)
	if err != nil {
		c.log.Errorf("media send failed to %s: %v", chatJid, err)
		return "", err
	}
	return fmt.Sprintf("%s|%d", resp.ID, resp.Timestamp.Unix()), nil
}


func (c *Client) DownloadMedia(mediaType, url, directPath string, mediaKey, fileEncSHA256, fileSHA256 []byte, fileLength int64, outPath string) error {
	var downloadable whatsmeow.DownloadableMessage
	switch mediaType {
	case "image":
		downloadable = &waE2E.ImageMessage{
			URL:           proto.String(url),
			DirectPath:    proto.String(directPath),
			MediaKey:      mediaKey,
			FileEncSHA256: fileEncSHA256,
			FileSHA256:    fileSHA256,
			FileLength:    proto.Uint64(uint64(fileLength)),
		}
	case "video":
		downloadable = &waE2E.VideoMessage{
			URL:           proto.String(url),
			DirectPath:    proto.String(directPath),
			MediaKey:      mediaKey,
			FileEncSHA256: fileEncSHA256,
			FileSHA256:    fileSHA256,
			FileLength:    proto.Uint64(uint64(fileLength)),
		}
	default:
		return fmt.Errorf("unsupported media type: %s", mediaType)
	}

	data, err := c.inner.Download(context.Background(), downloadable)
	if err != nil {
		return err
	}
	return os.WriteFile(outPath, data, 0644)
}