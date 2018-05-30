Photo Uploader
==============

Photo Uploader is a simple web service that provides file upload
functionality (to S3).

It's a solution to a programming challenge that some company sends to
its candidates.

Usage
-----

Running:

```
export AWS_ACCESS_KEY_ID="access_key"
export AWS_SECRET_ACCESS_KEY="secred"
export AWS_REGION=eu-west-1

./mvnw spring-boot:run \
       -Dupload.bucketName=BUCKET_NAME \
       -Dupload.keyPrefix=KEY_PREFIX
```

(or *build* + `java -jar [...]`)

Test call:

```
curl -F 'json={"user":1,"description":"test"};type=application/json' \
     -F 'photo=@PATH_TO_SOME_FILE.jpg' localhost:8080/photo/
```

Running automated tests:

```
./mvnw test   # unit tests only
./mvnw verify # unit + integration tests
```

Building:

```
./mvnw [clean] install
```
