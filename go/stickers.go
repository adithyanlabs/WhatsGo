package engine

import (
	"go.mau.fi/whatsmeow/appstate"
	"go.mau.fi/whatsmeow/types/events"
)

func nonNilStickerBytes(b []byte) []byte {
	if b == nil { return []byte{} }
	return b
}

func (c *Client) handleStickerEvent(evt any) {
	e, ok := evt.(*events.AppState)
	if !ok || len(e.Index) == 0 || e.Index[0] != appstate.IndexFavoriteSticker || e.SyncActionValue == nil { return }
	sa := e.SyncActionValue.GetStickerAction()
	if sa == nil || !sa.GetIsFavorite() { return }
	c.queue <- func() {
		c.cb.OnFavoriteSticker(sa.GetURL(), sa.GetDirectPath(), nonNilStickerBytes(sa.GetMediaKey()),
			nonNilStickerBytes(sa.GetFileEncSHA256()),
			int64(sa.GetFileLength()), sa.GetMimetype(), sa.GetIsLottie(),
			int64(sa.GetWidth()), int64(sa.GetHeight()))
	}
}
