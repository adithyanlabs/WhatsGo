package engine

import (
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types/events"
)

func (c *Client) processEvent(evt interface{}) {
	switch e := evt.(type) {
	case *events.PairSuccess:
		c.cb.OnConnected()

	case *events.PairError:
		c.cb.OnError(e.Error.Error())

	case *events.Connected:
		c.cb.OnConnected()

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
			status := "delivered"
			if e.Type == types.ReceiptTypeRead {
				status = "read"
			} else if e.Type == types.ReceiptTypeReadSelf {
				status = "read_self"
			}
			for _, msgID := range e.MessageIDs {
				c.cb.OnMessageStatus(msgID, status)
			}
		}

	case *events.Message:
		if e.Info.Chat.User == "status" && e.Info.Chat.Server == types.BroadcastServer {
			c.handleStatusMessage(e)
			return
		}

		img := e.Message.GetImageMessage()
		vid := e.Message.GetVideoMessage()

		if img != nil || vid != nil {
			var mediaType, caption, mimetype, url, directPath string
			var mediaKey, fileEncSHA256, fileSHA256, thumbnail []byte
			var fileLength int64
			var ctxInfo *waE2E.ContextInfo

			if img != nil {
				mediaType = "image"
				caption = img.GetCaption()
				mimetype = img.GetMimetype()
				url = img.GetURL()
				directPath = img.GetDirectPath()
				mediaKey = img.GetMediaKey()
				fileEncSHA256 = img.GetFileEncSHA256()
				fileSHA256 = img.GetFileSHA256()
				fileLength = int64(img.GetFileLength())
				thumbnail = img.GetJPEGThumbnail()
				ctxInfo = img.GetContextInfo()
			} else {
				mediaType = "video"
				caption = vid.GetCaption()
				mimetype = vid.GetMimetype()
				url = vid.GetURL()
				directPath = vid.GetDirectPath()
				mediaKey = vid.GetMediaKey()
				fileEncSHA256 = vid.GetFileEncSHA256()
				fileSHA256 = vid.GetFileSHA256()
				fileLength = int64(vid.GetFileLength())
				thumbnail = vid.GetJPEGThumbnail()
				ctxInfo = vid.GetContextInfo()
			}

			quotedID, quotedText, quotedSender := "", "", ""
			if ctxInfo != nil {
				quotedID = ctxInfo.GetStanzaID()
				quotedSender = ctxInfo.GetParticipant()
				if qm := ctxInfo.GetQuotedMessage(); qm != nil {
					quotedText = qm.GetConversation()
				}
			}
			if thumbnail == nil { thumbnail = []byte{} }
			if mediaKey == nil { mediaKey = []byte{} }
			if fileEncSHA256 == nil { fileEncSHA256 = []byte{} }
			if fileSHA256 == nil { fileSHA256 = []byte{} }

			c.cb.OnMediaMessage(
				e.Info.Chat.String(), e.Info.Sender.String(), e.Info.ID,
				mediaType, caption, e.Info.Timestamp.Unix(), e.Info.IsFromMe,
				url, directPath, mediaKey, fileEncSHA256, fileSHA256, fileLength, mimetype, thumbnail,
				quotedID, quotedText, quotedSender, e.Info.PushName,
			)
			return
		}

		text := e.Message.GetConversation()
		quotedID := ""
		quotedText := ""
		quotedSender := ""

		if ext := e.Message.GetExtendedTextMessage(); ext != nil {
			if text == "" {
				text = ext.GetText()
			}
			if ctx := ext.GetContextInfo(); ctx != nil {
				quotedID = ctx.GetStanzaID()
				quotedSender = ctx.GetParticipant()
				if qm := ctx.GetQuotedMessage(); qm != nil {
					quotedText = qm.GetConversation()
				}
			}
		}

		if text == "" {
			return
		}
		c.cb.OnMessage(
			e.Info.Chat.String(),
			e.Info.Sender.String(),
			e.Info.ID,
			text,
			e.Info.Timestamp.Unix(),
			e.Info.IsFromMe,
			quotedID,
			quotedText,
			e.Info.PushName,
			quotedSender,
		)
	}
}

func (c *Client) handleStatusMessage(e *events.Message) {
	img := e.Message.GetImageMessage()
	vid := e.Message.GetVideoMessage()
	ext := e.Message.GetExtendedTextMessage()

	var mediaType, text, mimetype, url, directPath string
	var mediaKey, fileEncSHA256, fileSHA256, thumbnail []byte
	var fileLength int64
	var bgColor int64 = 0xFF000000

	if img != nil {
		mediaType = "image"
		text = img.GetCaption()
		mimetype = img.GetMimetype()
		url = img.GetURL()
		directPath = img.GetDirectPath()
		mediaKey = img.GetMediaKey()
		fileEncSHA256 = img.GetFileEncSHA256()
		fileSHA256 = img.GetFileSHA256()
		fileLength = int64(img.GetFileLength())
		thumbnail = img.GetJPEGThumbnail()
	} else if vid != nil {
		mediaType = "video"
		text = vid.GetCaption()
		mimetype = vid.GetMimetype()
		url = vid.GetURL()
		directPath = vid.GetDirectPath()
		mediaKey = vid.GetMediaKey()
		fileEncSHA256 = vid.GetFileEncSHA256()
		fileSHA256 = vid.GetFileSHA256()
		fileLength = int64(vid.GetFileLength())
		thumbnail = vid.GetJPEGThumbnail()
	} else if ext != nil {
		mediaType = "text"
		text = ext.GetText()
		bgColor = int64(ext.GetBackgroundArgb())
	} else if conv := e.Message.GetConversation(); conv != "" {
		mediaType = "text"
		text = conv
	} else {
		return
	}

	if mediaKey == nil { mediaKey = []byte{} }
	if fileEncSHA256 == nil { fileEncSHA256 = []byte{} }
	if fileSHA256 == nil { fileSHA256 = []byte{} }
	if thumbnail == nil { thumbnail = []byte{} }

	c.cb.OnStatusMessage(
		e.Info.ID,
		e.Info.Sender.String(),
		e.Info.PushName,
		e.Info.Timestamp.Unix(),
		e.Info.IsFromMe,
		mediaType, text, bgColor,
		url, directPath, mediaKey, fileEncSHA256, fileSHA256, fileLength, mimetype, thumbnail,
	)
}

func (c *Client) handleEvent(evt interface{}) {
	switch evt.(type) {
	case *events.Message, *events.Receipt, *events.HistorySync:
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
