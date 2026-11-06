package engine

import (
	"context"
	"fmt"

	wa "go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/store/sqlstore"
	waLog "go.mau.fi/whatsmeow/util/log"
	_ "modernc.org/sqlite"
)

type LoginCallback interface {
	OnQrCode(payload string)
	OnPairingCode(code string)
	OnConnected()
	OnOwnName(name string)
	OnOwnAbout(about string)
	OnOwnAvatarChanged(pictureID string, removed bool)
	OnLoggedOut(reason string)
	OnError(msg string)
	OnMessageStatus(messageID string, status string)
	OnMessage(chatJID string, senderJID string, messageID string, text string, timestamp int64, fromMe bool, quotedID string, quotedText string, senderPushName string, quotedSenderJID string)
	OnMediaMessage(chatJID string, senderJID string, messageID string, mediaType string, caption string, timestamp int64, fromMe bool, url string, directPath string, mediaKey []byte, fileEncSHA256 []byte, fileSHA256 []byte, fileLength int64, mimetype string, thumbnail []byte, quotedID string, quotedText string, quotedSenderJID string, senderPushName string)

	OnStatusMessage(statusID string, senderJID string, senderName string, timestamp int64, fromMe bool, mediaType string, text string, backgroundColor int64, url string, directPath string, mediaKey []byte, fileEncSHA256 []byte, fileSHA256 []byte, fileLength int64, mimetype string, thumbnail []byte)
	OnStatusViewed(statusID string, viewerJID string)
}

type Client struct {
	inner     *wa.Client
	container *sqlstore.Container
	cb        LoginCallback
	log       waLog.Logger
	queue     chan func()
}

var ErrNotLoggedInLocal = fmt.Errorf("not logged in")

func NewClient(dbPath string, cb LoginCallback) (*Client, error) {
	logger := waLog.Stdout("Engine", "DEBUG", true)
	ctx := context.Background()
	container, err := sqlstore.New(ctx, "sqlite",
		"file:"+dbPath+"?_pragma=foreign_keys(1)&_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)",
		logger)
	if err != nil {
		return nil, err
	}
	device, err := container.GetFirstDevice(ctx)
	if err != nil {
		return nil, err
	}
	c := &Client{
		inner:     wa.NewClient(device, logger.Sub("Client")),
		container: container,
		cb:        cb,
		log:       logger.Sub("Events"),
	}
	c.queue = make(chan func(), 8192)
	go c.worker()
	c.inner.AddEventHandler(c.handleEvent)
	return c, nil
}

func (c *Client) worker() {
	for job := range c.queue {
		func() {
			defer func() {
				if r := recover(); r != nil {
					c.log.Errorf("callback panic: %v", r)
				}
			}()
			job()
		}()
	}
}

func (c *Client) Reconnect() error {
	if c.inner.IsConnected() {
		return nil
	}
	return c.inner.Connect()
}

func (c *Client) IsLoggedIn() bool {
	return c.inner.Store.ID != nil
}

func (c *Client) Logout() error {
	var err error
	if c.inner.IsConnected() {
		err = c.inner.Logout(context.Background())
	} else if c.inner.Store.ID != nil {
		_ = c.container.DeleteDevice(context.Background(), c.inner.Store)
	}
	c.inner.Disconnect()
	if c.container != nil {
		_ = c.container.Close()
	}
	return err
}

func (c *Client) Disconnect() {
	c.inner.Disconnect()
}
