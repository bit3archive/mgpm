MultiGitProjectManager
======================

MGPM helps you manage hundreds of projects to keep in sync with their remote repository.

Quick start
-----------

(1) create a new directory to store the projects in:

```bash
$ mkdir ~/my-projects
```

(2) put a `mgpm.yml` (see [wiki][wiki] for more detailed example) into this repository:

```bash
$ echo 'repositories:
  -
    type: github
    owner: bit3
' > ~/my-projects/mgpm.yml
```

(3) download the [latest build][releases]:

```bash
$ wget 'https://github.com/bit3/mgpm/releases/download/1.0.0-SNAPSHOT.12/mgpm-1.0.0-SNAPSHOT.12.jar' \
       -O ~/Downloads/mgpm.jar
```

(4) start mgpm in this directory:

```bash
$ cd ~/my-projects
$ java -jar ~/Downloads/mgpm.jar -i
```

HAVE FUN!!!

[wiki]: https://github.com/bit3/mgpm/wiki/mgpm.yml
[releases]: https://github.com/bit3/mgpm/releases
