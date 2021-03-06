[Repository](https://github.com/pallet/ssh-crate) &#xb7;
[Issues](https://github.com/pallet/ssh-crate/issues) &#xb7;
[API docs](http://palletops.com/ssh-crate/0.8/api) &#xb7;
[Annotated source](http://palletops.com/ssh-crate/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/ssh-crate/blob/develop/ReleaseNotes.md)

A [pallet](http://palletops.com/) crate to install and configure ssh.

### Dependency Information

```clj
:dependencies [[com.palletops/ssh-crate "0.8.0-SNAPSHOT"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-RC.1</th>
    <td>0.8.0-SNAPSHOT</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/ssh-crate/blob/0.8.0-SNAPSHOT/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/ssh-crate/blob/0.8.0-SNAPSHOT/'>Source</a></td>
  </tr>
</tbody>
</table>

## Usage

The ssh crate provides a `server-spec` function that returns a
server-spec. This server spec will install and run the ssh server (not the
dashboard).  You pass a map of options to configure ssh.  The `:config`
value should be a form that will be output as the
[ssh configuration](http://ssh.io/howto.html).

The `server-spec` provides an easy way of using the crate functions, and you can
use the following crate functions directly if you need to.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with the ssh server.  The settings are made up of
a `:master` key, with a value as a sequence of maps, each specifying a daemon
as specified in [master.cnf](http://www.ssh.org/master.5.html).

The `:supervision` key in the settings allows running ssh under `:runit`,
`:upstart` or `:nohup`.

The `install` function is responsible for actually installing ssh.  At
present installation from tarball url is the only supported method.
Installation from deb or rpm url would be nice to add, as these are now
available from the ssh site.

The `configure` function writes the ssh configuration file, using the form
passed to the :config key in the `settings` function.

The `run` function starts the ssh server.


## Live test on vmfest

For example, to run the live test on VMFest, using Ubuntu 12.04:

```sh
lein with-profile +vmfest pallet up --selectors ubuntu-12-04 --phases install,configure,test
lein with-profile +vmfest pallet down --selectors ubuntu-12-04
```

## License

Copyright (C) 2012, 2013 Hugo Duncan

Distributed under the Eclipse Public License.
