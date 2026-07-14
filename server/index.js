require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const axios = require('axios');
const FormData = require('form-data');

const app = express();
const PORT = process.env.PORT || 3000;

// Security and utility middlewares
app.use(helmet());
app.use(cors());
app.use(morgan('combined'));

// Standard JSON request body parser for direct small uploads
app.use(express.json());

// Raw body parser specifically for /api/upload-chunk to optimize speed and streaming
app.use('/api/upload-chunk', express.raw({ type: '*/*', limit: '50mb' }));

// Health Check
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', uptime: process.uptime() });
});

/**
 * Endpoint for direct small uploads (e.g. cover artwork)
 * Accepts standard JSON with base64, or multi-part.
 * To keep it super simple and secure, this endpoint can receive file via multipart
 * and forward it to Cloudinary.
 */
app.post('/api/upload', express.raw({ type: 'image/*', limit: '10mb' }), async (req, res) => {
  try {
    const cloudName = process.env.CLOUDINARY_CLOUD_NAME || 'wholetv';
    const preset = process.env.CLOUDINARY_UPLOAD_PRESET || 'wholetv_upload';
    
    if (!req.body || req.body.length === 0) {
      return res.status(400).json({ error: 'No data provided in the request body.' });
    }

    console.log(`[Direct Upload] Uploading image of size ${req.body.length} bytes to Cloudinary...`);
    
    const form = new FormData();
    form.append('file', req.body, { filename: 'cover.jpg', contentType: 'image/jpeg' });
    form.append('upload_preset', preset);

    const url = `https://api.cloudinary.com/v1_1/${cloudName}/auto/upload`;
    const response = await axios.post(url, form, {
      headers: {
        ...form.getHeaders()
      }
    });

    return res.status(200).json(response.data);
  } catch (error) {
    console.error('[Direct Upload Error]:', error.response?.data || error.message);
    return res.status(500).json({
      error: 'Failed to upload to Cloudinary',
      details: error.response?.data || error.message
    });
  }
});

/**
 * Resumable Chunked Upload API
 * Accepts raw binary bytes of a chunk in the body, and streams it straight to Cloudinary.
 * Android app sends:
 *  - X-Unique-Upload-Id: UUID representing the upload session
 *  - Content-Range: bytes START-END/TOTAL
 *  - Body: chunk binary
 */
app.post('/api/upload-chunk', async (req, res) => {
  try {
    const uploadId = req.headers['x-unique-upload-id'];
    const contentRange = req.headers['content-range'];
    const cloudName = process.env.CLOUDINARY_CLOUD_NAME || 'wholetv';
    const preset = process.env.CLOUDINARY_UPLOAD_PRESET || 'wholetv_upload';

    if (!uploadId || !contentRange) {
      return res.status(400).json({ error: 'Missing X-Unique-Upload-Id or Content-Range header.' });
    }

    if (!req.body || req.body.length === 0) {
      return res.status(400).json({ error: 'Chunk request body is empty.' });
    }

    console.log(`[Chunk Upload] UploadId: ${uploadId}, Content-Range: ${contentRange}, Chunk Size: ${req.body.length} bytes`);

    // Build the request body for Cloudinary
    const form = new FormData();
    form.append('file', req.body, { filename: 'chunk.mp4', contentType: 'application/octet-stream' });
    form.append('upload_preset', preset);

    const url = `https://api.cloudinary.com/v1_1/${cloudName}/auto/upload`;

    // Forward to Cloudinary with same X-Unique-Upload-Id and Content-Range headers
    const response = await axios.post(url, form, {
      headers: {
        ...form.getHeaders(),
        'X-Unique-Upload-Id': uploadId,
        'Content-Range': contentRange
      },
      maxContentLength: Infinity,
      maxBodyLength: Infinity
    });

    console.log(`[Chunk Success] Cloudinary response status: ${response.status}`);
    return res.status(response.status).json(response.data);
  } catch (error) {
    console.error('[Chunk Upload Error]:', error.response?.data || error.message);
    return res.status(error.response?.status || 500).json({
      error: 'Chunk upload to Cloudinary failed',
      details: error.response?.data || error.message
    });
  }
});

// Start Server
app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`WholeTV Secure Upload Backend running on port ${PORT}`);
  console.log(`Cloudinary Cloud: ${process.env.CLOUDINARY_CLOUD_NAME || 'wholetv'}`);
  console.log(`====================================================`);
});
