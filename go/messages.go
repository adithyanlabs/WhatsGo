package engine

import (
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

func (c *Client) dispatchMessage(e *events.Message) {
	if e.Info.Chat.User == "status" && e.Info.Chat.Server == types.BroadcastServer {
		c.handleStatusMessage(e)
		return
	}

	chat := c.normJID(e.Info.Chat)
	sender := c.normJID(e.Info.Sender)
	if st := e.Message.GetStickerMessage(); st != nil {
		quotedID, quotedText, quotedSender := "", "", ""
		if ci := st.GetContextInfo(); ci != nil {
			quotedID = ci.GetStanzaID()
			quotedSender = c.normJIDStr(ci.GetParticipant())
			if qm := ci.GetQuotedMessage(); qm != nil { quotedText = qm.GetConversation() }
		}
		nonNil := func(b []byte) []byte { if b == nil { return []byte{} }; return b }
		c.cb.OnStickerMessage(chat.String(), sender.String(), e.Info.ID, e.Info.Timestamp.Unix(), e.Info.IsFromMe,
			st.GetURL(), st.GetDirectPath(), nonNil(st.GetMediaKey()), nonNil(st.GetFileEncSHA256()), nonNil(st.GetFileSHA256()),
			int64(st.GetFileLength()), st.GetMimetype(), st.GetIsAnimated(), int64(st.GetWidth()), int64(st.GetHeight()),
			quotedID, quotedText, quotedSender, e.Info.PushName)
		return
	}
	if aud := e.Message.GetAudioMessage(); aud != nil {
		quotedID, quotedText, quotedSender := "", "", ""
		if ci := aud.GetContextInfo(); ci != nil {
			quotedID = ci.GetStanzaID()
			quotedSender = c.normJIDStr(ci.GetParticipant())
			if qm := ci.GetQuotedMessage(); qm != nil {
				quotedText = qm.GetConversation()
			}
		}
		nonNil := func(b []byte) []byte {
			if b == nil {
				return []byte{}
			}
			return b
		}
		c.cb.OnAudioMessage(
			chat.String(), sender.String(), e.Info.ID,
			e.Info.Timestamp.Unix(), e.Info.IsFromMe,
			aud.GetURL(), aud.GetDirectPath(),
			nonNil(aud.GetMediaKey()), nonNil(aud.GetFileEncSHA256()), nonNil(aud.GetFileSHA256()),
			int64(aud.GetFileLength()), aud.GetMimetype(), int64(aud.GetSeconds()), aud.GetPTT(), nonNil(aud.GetWaveform()),
			quotedID, quotedText, quotedSender, e.Info.PushName,
		)
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
			quotedSender = c.normJIDStr(ctxInfo.GetParticipant())
			if qm := ctxInfo.GetQuotedMessage(); qm != nil {
				quotedText = qm.GetConversation()
			}
		}
		if thumbnail == nil { thumbnail = []byte{} }
		if mediaKey == nil { mediaKey = []byte{} }
		if fileEncSHA256 == nil { fileEncSHA256 = []byte{} }
		if fileSHA256 == nil { fileSHA256 = []byte{} }

		c.cb.OnMediaMessage(
        	chat.String(), sender.String(), e.Info.ID,
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
			quotedSender = c.normJIDStr(ctx.GetParticipant())
			if qm := ctx.GetQuotedMessage(); qm != nil {
				quotedText = qm.GetConversation()
			}
		}
	}

	if text == "" {
		return
	}
	c.cb.OnMessage(
    	chat.String(),
    	sender.String(),
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
    	c.normJID(e.Info.Sender).String(),
		e.Info.PushName,
		e.Info.Timestamp.Unix(),
		e.Info.IsFromMe,
		mediaType, text, bgColor,
		url, directPath, mediaKey, fileEncSHA256, fileSHA256, fileLength, mimetype, thumbnail,
	)
}
