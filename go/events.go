package engine

import (
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

func (c *Client) processEvent(evt interface{}) {
	switch e := evt.(type) {
	case *events.PairSuccess:
            c.log.Infof("pair success, waiting for 515 reconnect + Connected")

	case *events.PairError:
		c.cb.OnError(e.Error.Error())

    case *events.Connected:
        c.cb.OnConnected()

	case *events.Message:
    	c.dispatchMessage(e)

    case *events.NewsletterJoin, *events.NewsletterLeave, *events.NewsletterMuteChange:
        c.cb.OnChannelEvent("changed", "")

    case *events.NewsletterLiveUpdate:
        c.handleChannelLive(e)

    case *events.HistorySync:
    	c.handleHistorySync(e)

    case *events.OfflineSyncPreview:
    	c.cb.OnOfflineSyncPreview(int64(e.Total), int64(e.Messages), int64(e.Notifications), int64(e.Receipts))

    case *events.OfflineSyncCompleted:
    	c.cb.OnOfflineSyncCompleted(int64(e.Count))

    case *events.Contact:
    	name := e.Action.GetFullName()
    	if name == "" {
    		name = e.Action.GetFirstName()
    	}
    	if name != "" {
    		c.cb.OnContactName(e.JID.ToNonAD().String(), name)
    	}

	case *events.PushNameSetting:
		name := c.inner.Store.PushName
		c.log.Infof("own push name updated: %s", name)
		c.cb.OnOwnName(name)

	case *events.Picture:
		if c.inner.Store.ID != nil && e.JID.User == c.inner.Store.ID.User {
			c.log.Infof("own avatar changed, removed=%v pictureID=%s", e.Remove, e.PictureID)
			c.cb.OnOwnAvatarChanged(e.PictureID, e.Remove)
		}

	case *events.UserAbout:
		if c.inner.Store.ID != nil && e.JID.User == c.inner.Store.ID.User {
			c.log.Infof("own about updated: %s", e.Status)
			c.cb.OnOwnAbout(e.Status)
		}

	case *events.LoggedOut:
		c.log.Warnf("logged out: %s", e.Reason.String())
		c.cb.OnLoggedOut(e.Reason.String())

	case *events.MarkChatAsRead:
    	if e.Action == nil || !e.Action.GetRead() {
    		return // marked unread: ignore
    	}
    	ts := e.Timestamp.Unix()
    	if r := e.Action.GetMessageRange(); r != nil && r.GetLastMessageTimestamp() > 0 {
    		ts = r.GetLastMessageTimestamp()
    	}
    	c.cb.OnChatRead(e.JID.ToNonAD().String(), ts)

	case *events.Receipt:
		if e.Chat.User == "status" && e.Chat.Server == types.BroadcastServer {
			if e.Type == types.ReceiptTypeRead {
				for _, msgID := range e.MessageIDs {
					c.cb.OnStatusViewed(msgID, e.Sender.String())
				}
			}
			return
		}
		if e.Type == types.ReceiptTypeDelivered || e.Type == types.ReceiptTypeRead || e.Type == types.ReceiptTypeReadSelf {
        	c.log.Infof("receipt type=%s fromMe=%v chat=%s ids=%v", e.Type, e.IsFromMe, e.Chat, e.MessageIDs)

        	status := "delivered"
        	switch {
        	case e.Type == types.ReceiptTypeReadSelf,
        		e.Type == types.ReceiptTypeRead && e.IsFromMe:
        		status = "read_self"
        	case e.Type == types.ReceiptTypeRead:
        		status = "read"
        	}
        	for _, msgID := range e.MessageIDs {
        		c.cb.OnMessageStatus(msgID, status)
        	}
        }

	case *events.ChatPresence:
		chat := c.normJID(e.Chat).String()
		sender := c.normJID(e.Sender).String()
		state := string(e.State)
		media := string(e.Media)
		c.cb.OnChatPresence(chat, sender, state, media)

	case *events.Presence:
		sender := c.normJID(e.From).String()
		online := !e.Unavailable
		lastSeen := int64(0)
		if !e.LastSeen.IsZero() {
			lastSeen = e.LastSeen.Unix()
		}
		c.cb.OnPresence(sender, online, lastSeen)
	}
}


func (c *Client) handleEvent(evt interface{}) {
	switch evt.(type) {
	case *events.Message, *events.Receipt, *events.MarkChatAsRead, *events.HistorySync,
		*events.OfflineSyncPreview, *events.OfflineSyncCompleted:
		c.queue <- func() { c.processEvent(evt) }
	default:
		defer func() {
			if r := recover(); r != nil {
				c.log.Errorf("event panic: %v", r)
			}
		}()
		c.processEvent(evt)
	}
}