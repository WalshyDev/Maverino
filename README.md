# Mavenerino

Maverino is a free to use Open-Source Software which allows people to easily host Maven repositories. Hosting private projects/GitHub repositories usually costs or is very limited on most others out there. Due to that, I created my own!

## Features
* Sub-repos - Allows for creating things like a /public/, /private/ or even like /software/ and /tools/
* Authentication - Don't worry about others being able to upload packages as authentication is built in! The config will even have a randomly generated key on the first run. Never will there be a default nonsecure key.
* Optional IP Whitelist - Only allow specific people to use the repository. You can limit this to yourself, your offices or whatever you'd like!
* Free! - Free to use, unlimited packages, no limits! As long as you have a server (Even a $2.50 VPS from like DigitalOcean or Vultr) there's no cost to you!!
* No limitations! - Any size, any amount, whatever you want!

## Configuration
Where the jar file is, there will be a `config.yml` generated. This is where you will configure things like the port, expiry time, repo ID, name, IP Whitelisting and the repos you'd like.

All `AUTH_KEY`s will be replaced by a randomly generated 40 character long string. 

Here is an example config:
```yaml
# The port for this web-server to run on
port: 8888
# Expiry time for the files in seconds (Default is 10 minutes)
expiryTime: 600

# The info displayed when a user goes to your repo in the browser
repoName: 'Example Repo'
repoId: 'example-repo'
displayedUrl: 'https://repo.example.com'

# Allow requests from only specific IP addresses. This supports IPv4 and IPv6!
ipWhitelist:
  enabled: false
  ips:
    - 'localhost'
    - '0:0:0:0:0:0:0:1'

# The available repos
repos:
  # An internal ID
  public:
    # The path of where this is served. For example: "/" would be https://repo.walshy.dev/
    # "/public/" would be https://repo.walshy.dev/public/
    path: '/'
    # The auth key needed to write packages to the repo.
    # If you wish to have no auth key you can remove this or set it to an empty string.
    # This key will be randomly generated on first run
    writeAuth: 'AUTH_KEY'
```

## Security
### IP Whitelist
You can enable IP Whitelisting and specify which IPs (IPv4 and IPv6 supported) are allowed to use the repository.
Example config:
```yaml
ipWhitelist:
  enabled: true
  ips:
    - 'localhost'
    - '0:0:0:0:0:0:0:1'
    - '1.1.1.1'
```

This would allow people on that server (o)

### Authentication
By default there's write authentication required. It is **highly** recommended to have at least write authentication!

To configure your Maven simply add this to your `~/.m2/settings.xml`
```
    <servers>
        <server>
            <id>${REPO_ID}</id>
            <password>${WRITE_KEY}</password>
        </server>
    </servers>
```
Obviously specifying your own repo ID and write key which can be configured in your `config.yml`