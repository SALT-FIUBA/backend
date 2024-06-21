listen {
  port = 4040
}

namespace "nginx" {
  source = {
    files = [
        "/mnt/nginxlogs/access.log"
    ]
  }
  print_log = true
  format = "$remote_addr - $remote_user [$time_local] $request_method \"$request_uri\" $status"

  relabel "remote_addr" { from = "remote_addr" }
  relabel "method" { from = "request_method" }
  relabel "request_uri" { from = "request_uri" }

  labels {
    app = "default"
  }
}
