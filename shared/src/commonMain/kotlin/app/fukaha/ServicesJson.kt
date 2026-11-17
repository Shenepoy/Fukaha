package app.fukaha

internal object ServicesJson {
    // Assembled from several community collections of embed-fixer lists.
    // Keep this embedded copy in sync with commonMain/resources/services.json.
    val RAW: String = """
{
  "bilibili": {
    "href": "https://bilibili.com",
    "name": "Bilibili",
    "services": [
      {
        "alternate_hosts": ["https://vxb23.tv"],
        "author": null,
        "description": "Bilibili embed fixer.",
        "host": "https://vxbilibili.com",
        "name": "BiliFix",
        "repo": null
      },
      {
        "author": "seriaati",
        "description": "Bilibili embed fixer.",
        "host": "https://fxbilibili.seria.moe",
        "name": "fxBilibili",
        "repo": "https://github.com/seriaati/fxBilibili"
      }
    ]
  },
  "bluesky": {
    "href": "https://bsky.app",
    "name": "Bluesky",
    "services": [
      {
        "author": "Lexedia",
        "description": "FixBluesky: Embed Bluesky links in Discord!",
        "host": "https://bskx.app",
        "alternate_hosts": ["https://bskyx.app"],
        "name": "VixBluesky",
        "repo": "https://github.com/Lexedia/VixBluesky"
      },
      {
        "author": "Rose",
        "description": "FixBluesky: Embed Bluesky links in Discord!",
        "host": "https://bsyy.app",
        "name": "FixBluesky",
        "repo": "https://github.com/ThornbushHQ/FixBluesky"
      },
      {
        "author": "Eduardo Cabral",
        "description": "Fix Bluesky embeds on Discord",
        "host": "https://bskye.app",
        "name": "bskye",
        "repo": "https://github.com/FerroEduardo/bskye"
      },
      {
        "author": "Dylan",
        "description": null,
        "host": "https://vxbsky.app",
        "name": "vxBsky",
        "repo": "https://github.com/dylanpdx/vxBsky"
      },
      {
        "author": "FxEmbed",
        "description": "Better social media embeds for Discord, Telegram, and more.",
        "host": "https://fxbsky.app",
        "name": "FxBluesky",
        "repo": "https://github.com/FxEmbed/FxEmbed"
      },
      {
        "author": "colduw",
        "description": "Bluesky embed fixer.",
        "host": "https://xbsky.app",
        "name": "xbsky",
        "repo": "https://github.com/colduw/xbsky"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://bs.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "deviantart": {
    "href": "https://deviantart.com",
    "name": "DeviantArt",
    "services": [
      {
        "author": "Tschrock",
        "description": "Fix DeviantArt embeds.",
        "host": "https://fixdeviantart.com",
        "name": "fixDeviantArt",
        "repo": "https://github.com/Tschrock/fixdeviantart"
      }
    ]
  },
  "facebook": {
    "href": "https://facebook.com",
    "name": "Facebook",
    "services": [
      {
        "author": "4pii4",
        "description": "Facebook embed fixer.",
        "host": "https://facebed.com",
        "name": "facebed",
        "repo": "https://github.com/4pii4/facebed"
      },
      {
        "author": null,
        "description": "Facebook post, Reel and video previews for messaging apps.",
        "host": "https://facecot.com",
        "name": "Facecot",
        "repo": null,
        "alternate_hosts": ["https://www.facecot.com"]
      },
      {
        "author": "seriaati",
        "description": "Separately hosted facebed instance for public Facebook previews.",
        "host": "https://facebed.seria.moe",
        "name": "facebed (seria)",
        "repo": "https://github.com/seriaati/facebed"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://fb.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "imgur": {
    "href": "https://imgur.com",
    "name": "Imgur",
    "services": [
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://imgurez.com",
        "name": "ImgurEZ",
        "repo": null
      }
    ]
  },
  "instagram": {
    "href": "https://instagram.com",
    "name": "Instagram",
    "services": [
      {
        "author": "Akmal",
        "description": "Unofficial InstaFix instance.",
        "host": "https://uuinstagram.com",
        "name": "uuinstagram",
        "repo": "https://github.com/Wikidepia/InstaFix"
      },
      {
        "author": "Akmal",
        "description": "Unofficial InstaFix instance.",
        "host": "https://eeinstagram.com",
        "name": "eeinstagram",
        "repo": "https://github.com/Wikidepia/InstaFix"
      },
      {
        "author": "Lainmode",
        "description": "Instagram embed proxy.",
        "host": "https://vxinstagram.com",
        "name": "InstagramEmbed",
        "repo": "https://github.com/Lainmode/InstagramEmbed-vxinstagram"
      },
      {
        "author": null,
        "description": "Instagram embed proxy.",
        "host": "https://kkinstagram.com",
        "name": "kkScript",
        "repo": null
      },
      {
        "author": null,
        "description": "Load balancer across Instagram embed instances.",
        "host": "https://zzinstagram.com",
        "name": "zzinstagram",
        "repo": null
      },
      {
        "author": "seirenkr",
        "description": "Instagram post and Reel previews with media, captions and stats.",
        "host": "https://oginstagram.com",
        "name": "OGInstagram",
        "repo": "https://github.com/seirenkr/OGInstagram"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://ig.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "pinterest": {
    "href": "https://pinterest.com",
    "name": "Pinterest",
    "services": [
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://pinterestez.com",
        "name": "PinterestEZ",
        "repo": null
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://pi.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "pixiv": {
    "href": "https://pixiv.net",
    "name": "Pixiv",
    "services": [
      {
        "alternate_hosts": ["https://ppxiv.net"],
        "author": "thelaao",
        "description": "pixiv embed fixer.",
        "host": "https://phixiv.net/",
        "name": "phixiv",
        "repo": "https://github.com/thelaao/phixiv"
      }
    ]
  },
  "reddit": {
    "href": "https://reddit.com",
    "name": "Reddit",
    "services": [
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://redditez.com",
        "name": "RedditEZ",
        "repo": null
      },
      {
        "author": "Dylan",
        "description": "Reddit embed fixer.",
        "host": "https://vxreddit.com",
        "name": "vxreddit",
        "repo": "https://github.com/dylanpdx/vxReddit"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://r.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "roblox": {
    "href": "https://roblox.com",
    "name": "Roblox",
    "services": [
      {
        "author": "false",
        "description": "Roblox game and profile previews.",
        "host": "https://www.fxroblox.com",
        "name": "FxRoblox",
        "repo": null,
        "alternate_hosts": ["https://fxroblox.com"]
      }
    ]
  },
  "snapchat": {
    "href": "https://snapchat.com",
    "name": "Snapchat",
    "services": [
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://snapchatez.com",
        "name": "SnapchatEZ",
        "repo": null
      }
    ]
  },
  "spotify": {
    "href": "https://open.spotify.com",
    "name": "Spotify",
    "services": [
      {
        "author": "dotconnexion",
        "description": "Spotify embed fixer.",
        "host": "https://fxspotify.com",
        "name": "fxspotify",
        "repo": "https://github.com/dotconnexion/fxspotify"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://sp.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "threads": {
    "href": "https://threads.net",
    "name": "Threads",
    "services": [
      {
        "author": "seriaati",
        "description": "Unofficial fixthreads instance.",
        "host": "https://fixthreads.seria.moe",
        "name": "fixthreads (seria)",
        "repo": "https://github.com/milanmdev/fixthreads"
      },
      {
        "author": "Everett Southwick",
        "description": "Threads embed fixer.",
        "host": "https://vxthreads.net",
        "name": "vxThreads",
        "repo": "https://github.com/everettsouthwick/vxThreads"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://th.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "tiktok": {
    "href": "https://tiktok.com",
    "name": "TikTok",
    "services": [
      {
        "author": "dargy",
        "description": "Fix stale and unresponsive TikTok embeds for Discord with just s/i/n",
        "host": "https://tnktok.com",
        "name": "fxTikTok",
        "repo": "https://github.com/okdargy/fxtiktok"
      },
      {
        "author": "Stella",
        "description": "tiktxk TikTok embed fixer",
        "host": "https://tiktxk.com/",
        "name": "tiktxk",
        "repo": "https://github.com/Britmoji/tiktxk"
      },
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://tiktokez.com/",
        "name": "TikTokEZ",
        "repo": null
      },
      {
        "author": null,
        "description": "TikTok embed proxy.",
        "host": "https://kktiktok.com",
        "name": "kkScript",
        "repo": null
      },
      {
        "author": "Xetera",
        "description": "TikTok embed fixer.",
        "host": "https://fixtiktok.com",
        "name": "FixTikTok",
        "repo": "https://github.com/Xetera/fixtiktok"
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://tt.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "tumblr": {
    "href": "https://tumblr.com",
    "name": "Tumblr",
    "services": [
      {
        "author": "knuxify",
        "description": "Make Tumblr embeds on other websites way better.",
        "host": "https://tpmblr.com/",
        "name": "fxtumblr",
        "repo": "https://github.com/knuxify/fxtumblr"
      },
      {
        "author": "MarkSuckerberg",
        "description": "Tumblr embed fixer.",
        "host": "https://txtumblr.com",
        "name": "TxTumblr",
        "repo": "https://github.com/MarkSuckerberg/txtumblr"
      }
    ]
  },
  "twitch": {
    "href": "https://twitch.tv",
    "name": "Twitch",
    "services": [
      {
        "author": "seriaati",
        "description": "Twitch clip embed fixer.",
        "host": "https://fxtwitch.seria.moe",
        "name": "fxtwitch",
        "repo": "https://github.com/seriaati/fxtwitch"
      }
    ]
  },
  "weibo": {
    "href": "https://weibo.com",
    "name": "Weibo",
    "services": [
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://weiboez.com",
        "name": "WeiboEZ",
        "repo": null
      }
    ]
  },
  "x": {
    "href": "https://x.com",
    "name": "X (Twitter)",
    "services": [
      {
        "alternate_hosts": ["https://vxtwitter.com"],
        "author": "Dylan",
        "description": "Fix X/Twitter video embeds in Discord (and Telegram!)",
        "host": "https://fixvx.com",
        "name": "BetterTwitFix",
        "repo": "https://github.com/dylanpdx/BetterTwitFix"
      },
      {
        "alternate_hosts": [
          "https://fxtwitter.com",
          "https://twittpr.com",
          "https://xfixup.com"
        ],
        "author": "FxEmbed",
        "description": "Better social media embeds for Discord, Telegram, and more.",
        "host": "https://fixupx.com",
        "name": "FxEmbed",
        "repo": "https://github.com/FxEmbed/FxEmbed"
      },
      {
        "author": null,
        "description": "A service that simplifies the process of sharing social media posts.",
        "host": "https://twitterez.com",
        "name": "TwitterEZ",
        "repo": null
      },
      {
        "author": null,
        "description": "Public-link previews from embeds.now; uses the same backend as its other platform domains.",
        "host": "https://x.embeds.now",
        "name": "embeds.now",
        "repo": null
      }
    ]
  },
  "youtube": {
    "href": "https://youtube.com",
    "name": "YouTube",
    "services": [
      {
        "alternate_hosts": ["https://koutu.be"],
        "author": "iGerman00",
        "description": "YouTube embed fixer, including Shorts.",
        "host": "https://koutube.com",
        "name": "Koutube",
        "repo": "https://github.com/iGerman00/koutube"
      }
    ]
  }
}
""".trimIndent()
}
