import QtQuick
import "../components"

// Home: the version profile, the big PLAY button (and Join Hypixel), install progress, updates and what's installed.
Item {
    id: page
    property real appear: 1   // animated by the page switcher

    Column {
        id: head
        anchors.left: parent.left
        anchors.right: profiles.left
        anchors.rightMargin: 12
        spacing: 4
        opacity: page.appear
        transform: Translate { y: 14 * (1 - page.appear) }
        Text {
            text: backend.accountName !== "" ? "Welcome back, " + backend.accountName : "Welcome to Hollow Client"
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 26
            font.weight: Font.Bold
        }
        Text {
            width: parent.width
            text: "Minecraft " + backend.mcVersion + "  ·  " + backend.modRows.slice(0, -1).map(function(r) { return r.name }).join(", ") + " and Hollow Client"
            color: app.dim
            font.family: app.fontFamily
            font.pixelSize: 14
            elide: Text.ElideRight
        }
    }
    ProfileSwitch {
        id: profiles
        objectName: "profileSwitch"
        anchors.right: parent.right
        anchors.verticalCenter: head.verticalCenter
        width: implicitWidth
        height: implicitHeight
        opacity: page.appear
    }

    Banner {
        id: banner
        anchors.top: head.bottom
        anchors.topMargin: 14
        anchors.left: parent.left
        anchors.right: parent.right
    }

    Banner {
        id: conflictBanner
        objectName: "conflictBanner"
        anchors.top: banner.bottom
        anchors.topMargin: shown ? 10 : 0
        anchors.left: parent.left
        anchors.right: parent.right
        text: backend.conflictText
        kind: "warn"
        action: ""
        closable: false
    }

    GlassPanel {
        id: hero
        anchors.top: conflictBanner.bottom
        anchors.topMargin: banner.shown || conflictBanner.shown ? 14 : 16
        anchors.left: parent.left
        anchors.right: parent.right
        height: Math.max(240, page.height - Math.max(head.height, profiles.height) - banner.height - conflictBanner.height - 30 - 214)
        opacity: Math.min(1, page.appear * 1.3)
        transform: Translate { y: 22 * (1 - page.appear) }

        // the sigil, softly glowing, drifting with the mouse
        Image {
            id: mark
            source: app.sigilUrl
            width: Math.min(hero.height * 0.62, hero.width * 0.26)
            height: width
            x: hero.width * 0.06 + (app.mouse.x / Math.max(1, app.frameWidth) - 0.5) * 10
            anchors.verticalCenter: parent.verticalCenter
            anchors.verticalCenterOffset: (app.mouse.y / Math.max(1, app.frameHeight) - 0.5) * 8
            smooth: true
            mipmap: true
            opacity: 0.95
            SequentialAnimation on scale {
                loops: Animation.Infinite
                running: backend.animatedBackground
                NumberAnimation { from: 1; to: 1.035; duration: 2600; easing.type: Easing.InOutSine }
                NumberAnimation { from: 1.035; to: 1; duration: 2600; easing.type: Easing.InOutSine }
            }
        }

        Column {
            id: playCol
            anchors.left: mark.right
            anchors.leftMargin: hero.width * 0.06
            anchors.right: parent.right
            anchors.rightMargin: 30
            anchors.verticalCenter: parent.verticalCenter
            spacing: 18

            PlayButton {
                width: Math.min(360, playCol.width)
                height: 86
                label: backend.gameRunning ? "RUNNING" : "PLAY"
                sub: backend.playSub
                ready: backend.canPlay
                onFired: backend.play()
            }
            Row {
                visible: backend.hasHypixelProfile
                spacing: 12
                HButton {
                    objectName: "homeJoinHypixel"
                    icon: "globe"
                    text: "Join Hypixel"
                    enabled: backend.canPlay
                    onClicked: backend.joinHypixel()
                }
                Text {
                    anchors.verticalCenter: parent.verticalCenter
                    width: playCol.width - 160
                    text: backend.profileHypixel ? "Starts this profile and connects to mc.hypixel.net." : "Switches to the 26.2 profile and connects to mc.hypixel.net."
                    color: app.faint
                    font.family: app.fontFamily
                    font.pixelSize: 12
                    wrapMode: Text.WordWrap
                }
            }
            InstallProgress {
                width: playCol.width
                visible: backend.isBusy && !backend.loggingIn
            }
            Row {
                visible: !backend.isBusy || backend.loggingIn
                spacing: 10
                width: playCol.width
                Rectangle {
                    width: 8
                    height: 8
                    radius: 4
                    anchors.verticalCenter: parent.verticalCenter
                    color: backend.statusKind === "error" ? "#f2706b" : (backend.statusKind === "ok" ? "#5bd18b" : app.accent)
                }
                Text {
                    width: parent.width - 18
                    text: backend.status
                    color: backend.statusKind === "error" ? "#ffb3b0" : app.dim
                    font.family: app.fontFamily
                    font.pixelSize: 13
                    wrapMode: Text.WordWrap
                }
            }
            HButton {
                visible: backend.loggingIn
                text: "Cancel sign-in"
                small: true
                onClicked: backend.cancelLogin()
            }
        }
    }

    Row {
        id: cards
        anchors.top: hero.bottom
        anchors.topMargin: 16
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        spacing: 16
        opacity: Math.min(1, page.appear * 1.15)
        transform: Translate { y: 30 * (1 - page.appear) }

        GlassPanel {
            width: (cards.width - cards.spacing) / 2
            height: cards.height
            Column {
                x: 18
                y: 16
                width: parent.width - 36
                spacing: 7
                SectionTitle { text: "Installed" }
                Repeater {
                    model: backend.versions
                    Item {
                        width: parent.width
                        height: 18
                        Text {
                            text: modelData.name
                            color: app.dim
                            font.family: app.fontFamily
                            font.pixelSize: 13
                        }
                        Text {
                            anchors.right: parent.right
                            text: modelData.value
                            color: app.text
                            font.family: app.fontFamily
                            font.pixelSize: 13
                            font.weight: Font.DemiBold
                        }
                    }
                }
            }
        }
        GlassPanel {
            width: (cards.width - cards.spacing) / 2
            height: cards.height
            Column {
                x: 18
                y: 16
                width: parent.width - 36
                spacing: 10
                SectionTitle { text: "Account" }
                Text {
                    width: parent.width
                    text: backend.accountText
                    color: app.dim
                    font.family: app.fontFamily
                    font.pixelSize: 13
                    wrapMode: Text.WordWrap
                }
                Row {
                    spacing: 8
                    HButton {
                        small: true
                        icon: "wrench"
                        text: "Repair"
                        enabled: !backend.isBusy
                        onClicked: backend.repair()
                    }
                    HButton {
                        small: true
                        icon: "folder"
                        text: "Game folder"
                        onClicked: backend.openFolder("game")
                    }
                }
            }
        }
    }
}
