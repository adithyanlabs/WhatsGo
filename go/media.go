package engine

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

// ---------------------------------------------------------------------------
// Upload progress + cancel
// ---------------------------------------------------------------------------

// localID -> context.CancelFunc
var uploadCancels sync.Map

// CancelUpload aborts an in-flight send started with the same localID.
func (c *Client) CancelUpload(localID string) {
	if v, ok := uploadCancels.Load(localID); ok {
		v.(context.CancelFunc)()
	}
}

func beginUpload(localID string) (context.Context, func()) {
	ctx, cancel := context.WithCancel(context.Background())
	if localID != "" {
		uploadCancels.Store(localID, cancel)
	}
	return ctx, func() {
		if localID != "" {
			uploadCancels.Delete(localID)
		}
		cancel()
	}
}

// progressFile wraps the encrypted temp file whatsmeow streams to the server.
// Reads after the final Seek(0) are the real network upload, so bytes read
// ~= bytes sent.
type progressFile struct {
	f        *os.File
	minSeeks int // seeks to start needed before reads count as upload
	seeks    int
	total    int64
	read     int64
	lastPct  int64
	report   func(pct int64)
}

func (p *progressFile) Read(b []byte) (int, error) {
	n, err := p.f.Read(b)
	if n > 0 && p.seeks >= p.minSeeks && p.total > 0 {
		p.read += int64(n)
		pct := p.read * 100 / p.total
		if pct > 99 {
			pct = 99
		}
		if pct >= p.lastPct+2 {
			p.lastPct = pct
			p.report(pct)
		}
	}
	return n, err
}

func (p *progressFile) Write(b []byte) (int, error) { return p.f.Write(b) }

func (p *progressFile) Seek(off int64, whence int) (int64, error) {
	if off == 0 && whence == io.SeekStart {
		p.seeks++
		p.read = 0
		p.lastPct = -2
		if st, err := p.f.Stat(); err == nil {
			p.total = st.Size()
		}
	}
	return p.f.Seek(off, whence)
}

// uploadFile streams filePath through encrypt -> temp file -> upload.
// Never loads the whole file in RAM. Temp file lives next to the source
// (app cache dir) because Android has no writable default TMPDIR.
func (c *Client) uploadFile(ctx context.Context, localID, filePath string, mt whatsmeow.MediaType) (whatsmeow.UploadResponse, string, error) {
	var zero whatsmeow.UploadResponse

	src, err := os.Open(filePath)
	if err != nil {
		return zero, "", err
	}
	defer src.Close()

	head := make([]byte, 512)
	n, _ := io.ReadFull(src, head)
	mimetype := http.DetectContentType(head[:n])
	if _, err = src.Seek(0, io.SeekStart); err != nil {
		return zero, "", err
	}

	tmp, err := os.CreateTemp(filepath.Dir(filePath), ".upload-*")
	if err != nil {
		return zero, "", err
	}
	defer func() {
		tmp.Close()
		os.Remove(tmp.Name())
	}()

	minSeeks := 1

	pf := &progressFile{
		f:        tmp,
		minSeeks: minSeeks,
		lastPct:  -2,
		report: func(p int64) {
			if localID != "" {
				c.cb.OnUploadProgress(localID, p)
			}
		},
	}

	resp, err := c.inner.UploadReader(ctx, src, pf, mt)
	return resp, mimetype, err
}

func result(id string, ts int64) string { return fmt.Sprintf("%s|%d", id, ts) }

// ---------------------------------------------------------------------------
// Senders. localID = Kotlin temp message id, used for progress + cancel.
// ---------------------------------------------------------------------------

func (c *Client) SendImageMessage(chatJid, filePath, caption string, thumbnail []byte, localID string) (string, error) {
	return c.sendMediaMessage(chatJid, filePath, caption, thumbnail, whatsmeow.MediaImage, localID)
}

func (c *Client) SendVideoMessage(chatJid, filePath, caption string, thumbnail []byte, localID string) (string, error) {
	return c.sendMediaMessage(chatJid, filePath, caption, thumbnail, whatsmeow.MediaVideo, localID)
}

func (c *Client) SendAudioMessage(chatJid, filePath string, seconds int64, ptt bool, waveform []byte, mimeType, localID string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}
	ctx, done := beginUpload(localID)
	defer done()
	fwd := forwardCtx(localID)

	up, sniffed, err := c.uploadFile(ctx, localID, filePath, whatsmeow.MediaAudio)
	if err != nil {
		return "", err
	}
	mimetype := mimeType
	if mimetype == "" {
		mimetype = sniffed
	}
	if ptt || mimetype == "application/ogg" {
		mimetype = "audio/ogg; codecs=opus"
	}
	audio := &waE2E.AudioMessage{
		URL:           proto.String(up.URL),
		DirectPath:    proto.String(up.DirectPath),
		MediaKey:      up.MediaKey,
		Mimetype:      proto.String(mimetype),
		FileEncSHA256: up.FileEncSHA256,
		FileSHA256:    up.FileSHA256,
		FileLength:    proto.Uint64(up.FileLength),
		Seconds:       proto.Uint32(uint32(seconds)),
		PTT:           proto.Bool(ptt),
		ContextInfo: fwd,
	}
	if len(waveform) > 0 {
		audio.Waveform = waveform
	}
	sent, err := c.inner.SendMessage(ctx, jid, &waE2E.Message{AudioMessage: audio})
	if err != nil {
		c.log.Errorf("audio send failed to %s: %v", chatJid, err)
		return "", err
	}
	return result(sent.ID, sent.Timestamp.Unix()), nil
}

func (c *Client) SendDocumentMessage(chatJid, filePath, fileName, mimeType, caption, localID string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}
	ctx, done := beginUpload(localID)
	defer done()
	fwd := forwardCtx(localID)

	up, sniffed, err := c.uploadFile(ctx, localID, filePath, whatsmeow.MediaDocument)
	if err != nil {
		return "", err
	}
	if mimeType == "" {
		mimeType = sniffed
	}
	if fileName == "" {
		fileName = "document"
	}
	msg := &waE2E.Message{DocumentMessage: &waE2E.DocumentMessage{
		URL:           proto.String(up.URL),
		DirectPath:    proto.String(up.DirectPath),
		MediaKey:      up.MediaKey,
		Mimetype:      proto.String(mimeType),
		Title:         proto.String(fileName),
		FileName:      proto.String(fileName),
		Caption:       proto.String(caption),
		FileEncSHA256: up.FileEncSHA256,
		FileSHA256:    up.FileSHA256,
		FileLength:    proto.Uint64(up.FileLength),
		ContextInfo: fwd,
	}}
	sent, err := c.inner.SendMessage(ctx, jid, msg)
	if err != nil {
		c.log.Errorf("document send failed to %s: %v", chatJid, err)
		return "", err
	}
	return result(sent.ID, sent.Timestamp.Unix()), nil
}

func (c *Client) sendMediaMessage(chatJid, filePath, caption string, thumbnail []byte, mediaType whatsmeow.MediaType, localID string) (string, error) {
	if c.inner.Store.ID == nil {
		return "", ErrNotLoggedInLocal
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}
	ctx, done := beginUpload(localID)
	defer done()
	fwd := forwardCtx(localID)

	up, mimetype, err := c.uploadFile(ctx, localID, filePath, mediaType)
	if err != nil {
		return "", err
	}

	var msg *waE2E.Message
	switch mediaType {
	case whatsmeow.MediaImage:
		msg = &waE2E.Message{
			ImageMessage: &waE2E.ImageMessage{
				URL:           proto.String(up.URL),
				DirectPath:    proto.String(up.DirectPath),
				MediaKey:      up.MediaKey,
				Mimetype:      proto.String(mimetype),
				FileEncSHA256: up.FileEncSHA256,
				FileSHA256:    up.FileSHA256,
				FileLength:    proto.Uint64(up.FileLength),
				Caption:       proto.String(caption),
				JPEGThumbnail: thumbnail,
				ContextInfo: fwd,

			},
		}
	case whatsmeow.MediaVideo:
		msg = &waE2E.Message{
			VideoMessage: &waE2E.VideoMessage{
				URL:           proto.String(up.URL),
				DirectPath:    proto.String(up.DirectPath),
				MediaKey:      up.MediaKey,
				Mimetype:      proto.String(mimetype),
				FileEncSHA256: up.FileEncSHA256,
				FileSHA256:    up.FileSHA256,
				FileLength:    proto.Uint64(up.FileLength),
				Caption:       proto.String(caption),
				JPEGThumbnail: thumbnail,
				ContextInfo: fwd,
			},
		}
	default:
		return "", fmt.Errorf("unsupported media type")
	}

	sent, err := c.inner.SendMessage(ctx, jid, msg)
	if err != nil {
		c.log.Errorf("media send failed to %s: %v", chatJid, err)
		return "", err
	}
	return result(sent.ID, sent.Timestamp.Unix()), nil
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
	case "audio", "voice":
		downloadable = &waE2E.AudioMessage{
			URL:           proto.String(url),
			DirectPath:    proto.String(directPath),
			MediaKey:      mediaKey,
			FileEncSHA256: fileEncSHA256,
			FileSHA256:    fileSHA256,
			FileLength:    proto.Uint64(uint64(fileLength)),
		}
	case "sticker":
		downloadable = &waE2E.StickerMessage{
			URL: proto.String(url), DirectPath: proto.String(directPath), MediaKey: mediaKey,
			FileEncSHA256: fileEncSHA256, FileSHA256: fileSHA256, FileLength: proto.Uint64(uint64(fileLength)),
		}
	case "document":
		downloadable = &waE2E.DocumentMessage{
			URL: proto.String(url), DirectPath: proto.String(directPath), MediaKey: mediaKey,
			FileEncSHA256: fileEncSHA256, FileSHA256: fileSHA256, FileLength: proto.Uint64(uint64(fileLength)),
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