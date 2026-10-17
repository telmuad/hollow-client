import QtQuick
import "../components"

// SkyBlock: mayor and election, the SkyBlock calendar, Jacob's contests, bazaar movers and margins, and the last
// session the Hollow mod saw. Information only; everything is read from public data or Hollow's own files.
Item {
    id: page
    property real appear: 1
    readonly property var d: skyblock.data
    readonly property var el: d.election || ({})
    readonly property var mayor: el.mayor || ({})
    readonly property var minister: el.minister || null
    readonly property var vote: el.election || null
    readonly property var src: d.sources || ({})
    readonly property var mv: d.movers || ({ up: [], down: [] })
    readonly property var session: d.session || ({})
    property real now: Date.now() / 1000

    property bool shown: appear > 0.5
    onShownChanged: if (shown) { now = Date.now() / 1000; skyblock.refreshIfDue() }
    Timer {
        interval: 1000
        repeat: true
        running: page.shown
        onTriggered: page.now = Date.now() / 1000
    }

    function dur(s) {
        s = Math.max(0, Math.floor(s))
        if (s < 60) return s + "s"
        if (s < 3600) return Math.floor(s / 60) + "m"
        if (s < 86400) return Math.floor(s / 3600) + "h " + Math.floor((s % 3600) / 60) + "m"
        return Math.floor(s / 86400) + "d " + Math.floor((s % 86400) / 3600) + "h"
    }
    function until(start, end) {
        if (now >= start && now < end) return "now, " + dur(end - now) + " left"
        return "in " + dur(start - now)
    }
    function clock(t) {
        var dt = new Date(t * 1000)
        var today = new Date()
        return dt.toDateString() === today.toDateString() ? Qt.formatTime(dt, "hh:mm") : Qt.formatDateTime(dt, "ddd hh:mm")
    }
    function coins(n) {
        var a = Math.abs(n)
        if (a >= 1e9) return (n / 1e9).toFixed(2) + "B"
        if (a >= 1e6) return (n / 1e6).toFixed(2) + "M"
        if (a >= 1e4) return (n / 1e3).toFixed(1) + "k"
        return n.toFixed(a >= 100 ? 0 : 1)
    }
    function pct(x) { return (x > 0 ? "+" : "") + (x * 100).toFixed(1) + "%" }
    function stagger(i) { return Math.max(0, Math.min(1, page.appear * 1.6 - i * 0.12)) }

    component Dim: Text {
        color: app.dim
        font.family: app.fontFamily
        font.pixelSize: 12
        wrapMode: Text.WordWrap
    }
    component Strong: Text {
        color: app.text
        font.family: app.fontFamily
        font.pixelSize: 13
        font.weight: Font.DemiBold
        elide: Text.ElideRight
    }
    component Card: GlassPanel {
        id: card
        default property alias body: col.data
        property string title: ""
        property string note: ""
        width: parent.width
        height: col.implicitHeight + 32
        Column {
            id: col
            x: 18
            y: 16
            width: card.width - 36
            spacing: 7
            Item {
                width: parent.width
                height: 20
                SectionTitle { text: card.title }
                Text {
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    text: card.note
                    color: app.faint
                    font.family: app.fontFamily
                    font.pixelSize: 11
                }
            }
        }
    }

    // ---- header
    Item {
        id: head
        anchors.left: parent.left
        anchors.right: parent.right
        height: 52
        opacity: page.appear
        transform: Translate { y: 14 * (1 - page.appear) }
        Column {
            spacing: 4
            Text {
                text: "SkyBlock"
                color: app.text
                font.family: app.fontFamily
                font.pixelSize: 26
                font.weight: Font.Bold
            }
            Text {
                text: (page.d.skyDate || "") + (page.d.offline ? "   ·   offline, showing saved data" : "")
                color: page.d.offline ? "#f5a524" : app.dim
                font.family: app.fontFamily
                font.pixelSize: 14
            }
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: 10
            HButton {
                small: true
                icon: "refresh"
                text: skyblock.loading ? "Loading..." : "Refresh"
                enabled: !skyblock.loading
                onClicked: skyblock.refresh()
            }
            HButton {
                objectName: "joinHypixel"
                primary: true
                icon: "globe"
                text: "Join Hypixel"
                visible: backend.hasHypixelProfile
                enabled: backend.canPlay
                onClicked: backend.joinHypixel()
            }
        }
    }

    Flickable {
        id: flick
        anchors.top: head.bottom
        anchors.topMargin: 14
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        contentWidth: width
        contentHeight: grid.height + 4
        clip: true
        boundsBehavior: Flickable.StopAtBounds

        Row {
            id: grid
            width: flick.width
            spacing: 14
            readonly property real colW: (width - 2 * spacing) / 3

            // ---- column 1: mayor and election
            Column {
                width: grid.colW
                spacing: 14
                opacity: page.stagger(0)
                transform: Translate { y: 18 * (1 - page.stagger(0)) }
                Card {
                    title: "Mayor"
                    note: page.mayor.name ? "term ends " + page.until(page.d.termEnds || 0, 0) : ""
                    Text {
                        text: page.mayor.name || "Not loaded yet"
                        color: page.mayor.name ? app.accent : app.faint
                        font.family: app.fontFamily
                        font.pixelSize: 22
                        font.weight: Font.Bold
                    }
                    Repeater {
                        model: page.mayor.perks || []
                        Column {
                            width: parent.width
                            spacing: 2
                            Strong { width: parent.width; text: modelData.name }
                            Dim {
                                width: parent.width
                                text: modelData.description
                                maximumLineCount: 3
                                elide: Text.ElideRight
                            }
                        }
                    }
                    Item { width: 1; height: 2; visible: page.minister !== null }
                    Strong {
                        width: parent.width
                        visible: page.minister !== null
                        text: page.minister ? "Minister " + page.minister.name + (page.minister.perk ? ": " + page.minister.perk.name : "") : ""
                    }
                    Dim {
                        width: parent.width
                        visible: page.minister !== null && page.minister.perk
                        text: page.minister && page.minister.perk ? page.minister.perk.description : ""
                        maximumLineCount: 3
                        elide: Text.ElideRight
                    }
                }
                Card {
                    visible: page.vote !== null
                    title: page.vote ? "Election, Year " + page.vote.year : "Election"
                    note: page.vote ? page.coins(page.vote.totalVotes) + " votes" : ""
                    Repeater {
                        model: page.vote ? page.vote.candidates : []
                        Item {
                            width: parent.width
                            height: 34
                            Strong { text: modelData.name; width: parent.width * 0.6 }
                            Text {
                                anchors.right: parent.right
                                text: (modelData.share * 100).toFixed(1) + "%"
                                color: index === 0 ? app.text : app.dim
                                font.family: app.fontFamily
                                font.pixelSize: 12
                                font.weight: index === 0 ? Font.Bold : Font.Normal
                            }
                            Rectangle {
                                y: 22
                                width: parent.width
                                height: 6
                                radius: 3
                                color: Qt.rgba(1, 1, 1, 0.06)
                                Rectangle {
                                    width: parent.width * modelData.share * page.stagger(1)
                                    height: parent.height
                                    radius: 3
                                    gradient: Gradient {
                                        orientation: Gradient.Horizontal
                                        GradientStop { position: 0; color: app.start }
                                        GradientStop { position: 1; color: app.end }
                                    }
                                    opacity: index === 0 ? 1 : 0.55
                                }
                            }
                        }
                    }
                }
            }

            // ---- column 2: calendar and Jacob's contests
            Column {
                width: grid.colW
                spacing: 14
                opacity: page.stagger(1)
                transform: Translate { y: 18 * (1 - page.stagger(1)) }
                Card {
                    title: "Coming up"
                    note: "local time"
                    Repeater {
                        model: page.d.events || []
                        Item {
                            readonly property bool live: page.now >= modelData.start && page.now < modelData.end
                            width: parent.width
                            height: 36
                            Rectangle {
                                width: 6
                                height: 6
                                radius: 3
                                y: 6
                                color: live ? "#5bd18b" : Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.7)
                            }
                            Strong { x: 14; width: parent.width * 0.58 - 14; text: modelData.name }
                            Text {
                                anchors.right: parent.right
                                text: page.until(modelData.start, modelData.end)
                                color: live ? "#5bd18b" : app.text
                                font.family: app.fontFamily
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                            }
                            Dim {
                                x: 14
                                y: 18
                                width: parent.width - 14
                                text: (live ? "" : page.clock(modelData.start) + "  ·  ") + modelData.detail
                                elide: Text.ElideRight
                                wrapMode: Text.NoWrap
                                color: app.faint
                            }
                        }
                    }
                }
                Card {
                    title: "Jacob's contests"
                    note: page.src.contests && page.src.contests.missing ? "crops not loaded" : "every hour at :15"
                    Repeater {
                        model: page.d.contests || []
                        Item {
                            readonly property bool live: page.now >= modelData.start && page.now < modelData.end
                            width: parent.width
                            height: 38
                            Strong { text: page.clock(modelData.start); width: parent.width * 0.5 }
                            Text {
                                anchors.right: parent.right
                                text: page.until(modelData.start, modelData.end)
                                color: live ? "#5bd18b" : app.dim
                                font.family: app.fontFamily
                                font.pixelSize: 12
                            }
                            Dim {
                                y: 18
                                width: parent.width
                                text: modelData.crops.length ? modelData.crops.join("  ·  ") : "crops unknown"
                                color: modelData.crops.length ? app.text : app.faint
                                wrapMode: Text.NoWrap
                                elide: Text.ElideRight
                            }
                        }
                    }
                }
            }

            // ---- column 3: bazaar and the last session
            Column {
                width: grid.colW
                spacing: 14
                opacity: page.stagger(2)
                transform: Translate { y: 18 * (1 - page.stagger(2)) }
                Card {
                    title: "Bazaar movers"
                    note: page.d.moversSince ? "since " + page.clock(page.d.moversSince) : ""
                    Dim {
                        width: parent.width
                        visible: (page.mv.up || []).length + (page.mv.down || []).length === 0
                        text: page.src.bazaar && !page.src.bazaar.missing
                              ? "Price changes show up once the launcher has seen the bazaar twice, at least 10 minutes apart."
                              : "Not loaded yet."
                        color: app.faint
                    }
                    Repeater {
                        model: (page.mv.up || []).concat(page.mv.down || [])
                        Item {
                            width: parent.width
                            height: 20
                            Strong { width: parent.width * 0.62; text: modelData.name; font.weight: Font.Medium; font.pixelSize: 12 }
                            Text {
                                anchors.right: parent.right
                                text: page.coins(modelData.price) + "  " + page.pct(modelData.change)
                                color: modelData.change > 0 ? "#5bd18b" : "#f2706b"
                                font.family: app.fontFamily
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                            }
                        }
                    }
                }
                Card {
                    title: "Bazaar margins"
                    note: "before tax"
                    Dim {
                        width: parent.width
                        visible: (page.d.margins || []).length === 0
                        text: "Not loaded yet."
                        color: app.faint
                    }
                    Repeater {
                        model: page.d.margins || []
                        Item {
                            width: parent.width
                            height: 20
                            Strong { width: parent.width * 0.6; text: modelData.name; font.weight: Font.Medium; font.pixelSize: 12 }
                            Text {
                                anchors.right: parent.right
                                text: page.coins(modelData.margin) + "  (" + (modelData.percent * 100).toFixed(1) + "%)"
                                color: app.text
                                font.family: app.fontFamily
                                font.pixelSize: 12
                            }
                        }
                    }
                }
                Card {
                    title: page.session.profile ? "Last session · " + page.session.profile : "Last session"
                    note: page.session.updated ? page.dur(page.now - page.session.updated) + " ago" : ""
                    Dim {
                        width: parent.width
                        visible: !(page.session.rows && page.session.rows.length)
                        text: "Play SkyBlock with Hollow Client and your session totals show up here."
                        color: app.faint
                    }
                    Repeater {
                        model: page.session.rows || []
                        Item {
                            width: parent.width
                            height: 19
                            Dim { text: modelData.label; wrapMode: Text.NoWrap }
                            Strong { anchors.right: parent.right; text: modelData.value; font.pixelSize: 12 }
                        }
                    }
                }
            }
        }
    }
}
