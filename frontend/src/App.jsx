import { useState, useCallback } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE || '';

export default function App() {
  const [file, setFile] = useState(null);
  const [zoomPercent, setZoomPercent] = useState(0);
  const [status, setStatus] = useState(null);
  const [requestId, setRequestId] = useState(null);
  const [downloadUrl, setDownloadUrl] = useState(null);
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);

  const onFileChange = useCallback((e) => {
    const f = e.target?.files?.[0];
    if (f) setFile(f);
  }, []);

  const onDrop = useCallback((e) => {
    e.preventDefault();
    const f = e.dataTransfer?.files?.[0];
    if (f) setFile(f);
  }, []);

  const onDragOver = useCallback((e) => {
    e.preventDefault();
  }, []);

  const upload = useCallback(async () => {
    if (!file) {
      setError('Select a BMP file first.');
      return;
    }
    setError(null);
    setStatus('pending');
    setDownloadUrl(null);
    setUploading(true);
    const url = `${API_BASE}/api/upload`;
    console.log('[Frontend] Upload start:', file.name, file.size, 'bytes, zoom%', zoomPercent, '->', url);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('zoomPercent', String(zoomPercent));
      const res = await fetch(url, { method: 'POST', body: form });
      console.log('[Frontend] Upload response:', res.status, res.statusText);
      if (!res.ok) {
        const j = await res.json().catch(() => ({}));
        const msg = j.error || `Upload failed: ${res.status}`;
        console.error('[Frontend] Upload error:', msg);
        throw new Error(msg);
      }
      const data = await res.json();
      console.log('[Frontend] Upload ok, requestId=', data.requestId, 'pictureId=', data.pictureId);
      setRequestId(data.requestId);
      pollStatus(data.requestId);
    } catch (err) {
      console.error('[Frontend] Upload failed:', err.message);
      setError(err.message);
      setStatus('error');
      setUploading(false);
    }
  }, [file, zoomPercent]);

  const pollStatus = useCallback(async (id) => {
    const maxAttempts = 120;
    console.log('[Frontend] Poll job-status', id);
    for (let i = 0; i < maxAttempts; i++) {
      try {
        const res = await fetch(`${API_BASE}/api/job-status/${id}`);
        if (!res.ok) {
          console.warn('[Frontend] job-status', res.status);
          break;
        }
        const data = await res.json();
        if (i === 0 || (i % 10 === 0)) console.log('[Frontend] job-status', id, data.status);
        if (data.status === 'ready') {
          console.log('[Frontend] Job ready, downloadUrl=', data.downloadUrl);
          setStatus('ready');
          setDownloadUrl(data.downloadUrl || null);
          setUploading(false);
          return;
        }
        if (data.downloadUrl && data.downloadUrl.startsWith('error:')) {
          setError(data.downloadUrl);
          setStatus('error');
          setUploading(false);
          return;
        }
      } catch (e) {
        console.warn('[Frontend] poll error:', e.message);
      }
      await new Promise((r) => setTimeout(r, 1000));
    }
    console.error('[Frontend] Timeout waiting for job', id);
    setError('Timeout waiting for processed image.');
    setStatus('error');
    setUploading(false);
  }, []);

  return (
    <div className="app">
      <h1>BMP Zoom</h1>
      <p className="subtitle">Upload a BMP, set zoom %, and download the result.</p>

      <div
        className={`upload-zone ${file ? 'dragover' : ''}`}
        onDrop={onDrop}
        onDragOver={onDragOver}
      >
        <input
          type="file"
          id="file"
          accept=".bmp,image/bmp"
          onChange={onFileChange}
        />
        <label htmlFor="file">
          {file ? file.name : 'Drop a BMP here or click to select'}
        </label>
        {file && <div className="selected-file">Selected: {file.name}</div>}
      </div>

      <div className="zoom-control">
        <label htmlFor="zoom">Zoom %</label>
        <input
          id="zoom"
          type="number"
          min="10"
          max="500"
          value={String(zoomPercent ?? 0)}
          onChange={(e) => setZoomPercent(Number(e.target.value))}
        />
        <span>e.g. 50 = shrink, 200 = 2×</span>
      </div>

      <button
        className="btn btn-primary"
        onClick={upload}
        disabled={uploading || !file}
      >
        {uploading ? 'Processing…' : 'Upload & zoom'}
      </button>

      {error && <div className="error-msg">{error}</div>}

      {status && (
        <div className={`status ${status}`}>
          {status === 'pending' && 'Image is being processed. Please wait…'}
          {status === 'ready' && (
            <>
              Ready.{' '}
              {downloadUrl && !downloadUrl.startsWith('error:') && (
                <a
                  className="download-link"
                  href={downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Download BMP
                </a>
              )}
            </>
          )}
          {status === 'error' && (downloadUrl || error || 'Something went wrong.')}
        </div>
      )}
    </div>
  );
}
