# Deploy to Railway

This project includes a Dockerfile for Railway's Java 17 deployment.

1. Push this project to a GitHub repository.
2. In Railway, create a project and choose **Deploy from GitHub repo**, then select the repository.
3. In the service's **Variables** settings, add `TMDB_API_KEY` with your API key from [The Movie Database](https://www.themoviedb.org/settings/api). The app requires this variable to start.
4. Trigger a deployment. Railway detects the Dockerfile and builds the application.
5. Once the deployment is healthy, open the service's **Settings** and generate a public domain.

The app listens on Railway's `PORT` environment variable and serves the website at `/`. The `/api/movies` endpoint uses the TMDB key; the other booking endpoints use in-memory demo data, which resets when the service restarts.
