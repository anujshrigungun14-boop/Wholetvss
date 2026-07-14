# WholeTV Secure Backend Server

This is a production-grade secure node backend for the **WholeTV** Android streaming platform. It prevents exposing your **Cloudinary API Secrets** or credentials inside the Android client APK, while securely handling extremely large, chunked, resumable video uploads.

## Features

- **No Secrets inside the App**: Cloudinary API parameters are managed exclusively on this server.
- **Resumable Chunked Video Uploads**: Handles files of 3 GB, 5 GB, 10 GB+ reliably with automatic retry on failed chunks.
- **Fast Pipe**: Binary chunks are streamed from Android to this server, which then streams them directly to Cloudinary.
- **Security Middlewares**: Uses `helmet`, `cors`, and rate limits to secure the upload endpoint.

## Deployment Instructions

### Method 1: Deploying to Render (Recommended)

1. Sign up for a free account on [Render](https://render.com).
2. Click **New +** and select **Web Service**.
3. Connect your GitHub repository containing this folder (or create a new repository with this folder's contents).
4. Set the following environment variables:
   - `PORT`: `3000` or leave default
   - `CLOUDINARY_CLOUD_NAME`: Your Cloudinary Cloud Name (e.g. `wholetv`)
   - `CLOUDINARY_UPLOAD_PRESET`: Your Cloudinary chunked upload preset (e.g. `wholetv_upload` - make sure to enable chunked uploads on Cloudinary with standard chunk sizes of 5MB - 10MB)
5. Set the build command to: `npm install`
6. Set the start command to: `npm start`
7. Click **Deploy Web Service**. You will get a public URL (e.g. `https://wholetv-backend.onrender.com`).
8. Paste this URL into the **Settings** panel or configure it inside your Android application.

## API Documentation

### 1. Health Check
- **URL**: `/health`
- **Method**: `GET`
- **Response**: `{"status": "OK", "uptime": 45.2}`

### 2. Direct Image Upload (Covers)
- **URL**: `/api/upload`
- **Method**: `POST`
- **Header**: `Content-Type: image/jpeg`
- **Body**: Raw image bytes
- **Response**: Cloudinary standard JSON response containing `secure_url`.

### 3. Chunked Resumable Upload (Videos)
- **URL**: `/api/upload-chunk`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/octet-stream`
  - `X-Unique-Upload-Id`: Unique UUID string for this video session
  - `Content-Range`: `bytes START-END/TOTAL` (e.g. `bytes 0-5242879/10485760`)
- **Body**: Raw binary chunk bytes
- **Response**: Standard Cloudinary chunk upload response. Once the last chunk is uploaded, it returns the final `secure_url`.
