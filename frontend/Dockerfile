FROM nginx:1.25-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN mkdir -p /usr/share/nginx/html && \
    printf '<!DOCTYPE html>\n<html lang="en">\n<head><meta charset="UTF-8"><title>Mockwise</title></head>\n<body><h1>Mockwise</h1><p>Frontend coming soon.</p></body>\n</html>\n' \
    > /usr/share/nginx/html/index.html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
