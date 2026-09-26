# Third-party notices

## Quest Helper

The quest data in every bundle this repository publishes is extracted from Quest Helper
(https://github.com/Zoinkwiz/quest-helper), by Zoinkwiz and contributors, as distributed by
RuneLite's plugin hub. It is redistributed under Quest Helper's licence, reproduced in full below
and embedded in each bundle's `attribution.licenseText`. Quest Helper's code is not redistributed:
its jar is downloaded at build time and never committed or published.

```
BSD 2-Clause License

Copyright (c) 2020, Zoinkwiz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## RuneLite plugin-hub certificate

`pipeline/src/main/resources/com/slest/osrsdata/pipeline/runelite-externalplugins.crt` is RuneLite's
public plugin-hub certificate, copied byte for byte from `net/runelite/client/externalplugins/externalplugins.crt`
in the RuneLite client 1.12.39 (https://github.com/runelite/runelite, BSD 2-Clause License). It is
used only to verify the signature on the plugin hub's manifest.

## Build-time dependencies

The exporter and the pipeline are built against RuneLite's `client` and `runelite-api`
(https://github.com/runelite/runelite, BSD 2-Clause), Gson (Apache License 2.0) and JUnit 5
(Eclipse Public License 2.0). They are resolved from their Maven repositories when building and
are not redistributed by this repository or its releases.
