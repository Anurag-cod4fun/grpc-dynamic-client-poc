# gRPC Dynamic Client POC

Calls **any** external gRPC backend at runtime, given only its compiled `FileDescriptorSet`
— no generated Java stubs, no `.proto` baked into the classpath per backend.

## Modules

- **`dynamic-client`** — the Spring Boot app. Register a backend's descriptor set, then
  invoke any method on it by name with a JSON payload.
- **`test-server`** — a plain gRPC server (standard generated stubs, no Spring) exposing
  a `Greeter` service. Exists only so you have something real to call end-to-end.

## How the dynamic invocation actually works

1. `DescriptorRegistry` parses a `FileDescriptorSet` (protoc's `--descriptor_set_out`
   output) into live `com.google.protobuf.Descriptors.FileDescriptor` objects, resolving
   `import` dependencies recursively. This is the same information generated `*.java`
   stub classes are built from — we're just doing it at runtime instead of at compile time.
2. Given `"package.Service/Method"`, it looks up the `Descriptors.MethodDescriptor`
   (message shapes + streaming type).
3. `DynamicGrpcInvoker` builds a plain `io.grpc.MethodDescriptor<DynamicMessage,
   DynamicMessage>` by hand (this is normally auto-generated inside `XyzGrpc.java`), using
   `ProtoUtils.marshaller(...)` for (de)serialization.
4. The JSON payload you send is parsed straight into a `DynamicMessage` via
   `JsonFormat.parser().merge(...)`, and the response `DynamicMessage` is serialized back
   to JSON via `JsonFormat.printer()`.
5. `ClientCalls.blockingUnaryCall` / `blockingServerStreamingCall` /
   `asyncClientStreamingCall` / `asyncBidiStreamingCall` do the actual invocation — the
   exact same static helpers a generated stub calls internally.

`GrpcChannelPool` caches one `ManagedChannel` per target host:port so repeated calls
reuse the connection.

## Build & run

Requires Maven Central access (this sandbox doesn't have it — build locally) and,
for `test-server` only, `protoc`/`protoc-gen-grpc-java` (pulled automatically by the
`protobuf-maven-plugin` via `os-maven-plugin`).

```bash
mvn -pl test-server -am package
mvn -pl dynamic-client -am spring-boot:run   # runs on :8080, separate terminal
```

Start the test gRPC server:
```bash
java -jar test-server/target/test-server-1.0.0-SNAPSHOT-jar-with-dependencies.jar 6565
```

### 1. Generate a descriptor set for the target backend

For any backend you want to call, ask its team for their `.proto` (or a compiled
descriptor set directly) and run:
```bash
./scripts/generate-descriptor.sh
# -> writes greeter.desc
```

### 2. Register it with the dynamic client

```bash
curl -F file=@greeter.desc http://localhost:8080/descriptors/greeter-test-service
```
Response confirms the methods it found:
```json
{
  "serviceId": "greeter-test-service",
  "registeredMethods": [
    "helloworld.Greeter/SayHello",
    "helloworld.Greeter/SayHelloStream"
  ]
}
```

### 3. Call any method on it — unary

```bash
curl -X POST http://localhost:8080/grpc/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "greeter-test-service",
    "host": "localhost",
    "port": 6565,
    "plaintext": true,
    "fullMethodName": "helloworld.Greeter/SayHello",
    "payloadsJson": ["{\"name\": \"Anurag\"}"]
  }'
```
```json
{
  "methodType": "UNARY",
  "responsesJson": ["{\"message\": \"Hello, Anurag! (from real generated stubs)\"}"]
}
```

### 4. Server-streaming — same shape, just a different method name

```bash
curl -X POST http://localhost:8080/grpc/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "greeter-test-service",
    "host": "localhost",
    "port": 6565,
    "plaintext": true,
    "fullMethodName": "helloworld.Greeter/SayHelloStream",
    "payloadsJson": ["{\"name\": \"Anurag\"}"]
  }'
```
Returns 3 messages in `responsesJson` — no code change needed, the invoker read the
streaming flag off the descriptor.

Notice **nothing in `dynamic-client` was recompiled or redeployed** to add support for a
new backend — only the descriptor set changed. That's the point: register a new
`serviceId` with its own descriptor set and you can call it immediately.

## What's intentionally simplified for a POC (call these out before this goes anywhere near prod)

- **Descriptor storage is in-memory** (`InMemoryDescriptorStore`). Swap for a
  `VaultDescriptorStore implements DescriptorStore` reading bytes from your Vault KV
  path — the rest of the code doesn't change since everything depends on the
  `DescriptorStore` interface, not the implementation.
- **Plaintext channels only.** Real external backends need TLS/mTLS — extend
  `GrpcChannelPool` to build an `SslContext` per target the same way you already handle
  mTLS for your Feign clients.
- **No descriptor versioning/drift detection.** Worth adding a hash check (or optional
  reflection-based verification) so a backend redeploying with a breaking proto change
  fails loudly instead of silently mismatching fields.
- **No auth/interceptors.** Add a `ClientInterceptor` for auth headers, tracing, retry,
  and metrics — attach it in `GrpcChannelPool` when building each channel.
- **No connection-level circuit breaking/retry policy** — worth wiring in gRPC's
  built-in retry config or a Resilience4j wrapper per target.
"# grpc-dynamic-client-poc" 
"# grpc-dynamic-client-poc" 
