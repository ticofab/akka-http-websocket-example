# Akka Http Websocket Example

I recently needed to implement a bi-directional websocket channel where each connected client is handled by an actor. All
examples I could find were somewhat more complicated and mostly about the chat use-case., so here is my simpler example.

* Each connection gets assigned to an actor
* This actor will reply with `"Hallo " + s` when receiving a string message
* This actor will pipe down to the client any `int` value it receives.

All information is described on my blog: [Akka Http Websocket Example]()

## License

    Copyright 2018 Fabio Tiriticco

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

