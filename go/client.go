package engine

import (
	"context"
	"fmt"

	wa "go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/store/sqlstore"
	waLog "go.mau.fi/whatsmeow/util/log"
	_ "modernc.org/sqlite"
	waStore "go.mau.fi/whatsmeow/store"
    "go.mau.fi/whatsmeow/proto/waCompanionReg"
    "google.golang.org/protobuf/proto"
    "sync"

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
	OnAudioMessage(chatJID string, senderJID string, messageID string, timestamp int64, fromMe bool, url string, directPath string, mediaKey []byte, fileEncSHA256 []byte, fileSHA256 []byte, fileLength int64, mimetype string, seconds int64, ptt bool, waveform []byte, quotedID string, quotedText string, quotedSenderJID string, senderPushName string)

	OnStatusMessage(statusID string, senderJID string, senderName string, timestamp int64, fromMe bool, mediaType string, text string, backgroundColor int64, url string, directPath string, mediaKey []byte, fileEncSHA256 []byte, fileSHA256 []byte, fileLength int64, mimetype string, thumbnail []byte)
	OnStatusViewed(statusID string, viewerJID string)
	OnHistoryContact(jid string, name string)
	OnHistoryChat(chatJID string, name string, unreadCount int64)
	OnHistorySync(syncType string, progress int64)
	OnOfflineSyncPreview(total, messages, notifications, receipts int64)
	OnOfflineSyncCompleted(count int64)
	OnContactName(jid string, name string)
	OnChatRead(chatJID string, timestamp int64)
	OnChatPresence(chatJID string, senderJID string, state string, media string)
	OnPresence(senderJID string, online bool, lastSeen int64)
	OnChannelEvent(kind string, jid string)
}

type Client struct {
	inner     *wa.Client
	container *sqlstore.Container
	cb        LoginCallback
	log       waLog.Logger
	queue     chan func()
	lidCache  sync.Map
}

var ErrNotLoggedInLocal = fmt.Errorf("not logged in")

func NewClient(dbPath string, cb LoginCallback) (*Client, error) {
	logger := waLog.Stdout("Engine", "WARN", true)
	ctx := context.Background()
	container, err := sqlstore.New(ctx, "sqlite",
            "file:"+dbPath+"?_pragma=foreign_keys(1)&_pragma=busy_timeout(10000)"+
                "&_pragma=journal_mode(WAL)&_pragma=synchronous(NORMAL)"+
                "&_pragma=cache_size(-16000)&_pragma=temp_store(MEMORY)",
            logger)
	if err != nil {
		return nil, err
	}
	device, err := container.GetFirstDevice(ctx)
	if err != nil {
		return nil, err
	}
	
	waStore.DeviceProps.Os = proto.String("WhatsGo")
    waStore.DeviceProps.PlatformType = waCompanionReg.DeviceProps_ANDROID_PHONE.Enum()

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
