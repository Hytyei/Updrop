package app.updrop.data

/** The variant Updrop suggests, with the reasons shown to the user. */
data class Recommendation(val variant: Variant, val reasons: List<String>)

object Recommender {
    /**
     * Picks the file to download:
     *  1. it must run on this phone's CPU;
     *  2. same CPU build as the installed copy (or the phone's best ABI when not installed);
     *  3. the flavour the user pinned or downloaded last (Premium / Offline / Mod-Extra…);
     *  4. otherwise the first link on the page (Farsroid lists the main build first).
     */
    fun pick(app: TrackedApp, installed: Installed?, supported: List<String> = DeviceInfo.supportedAbis): Recommendation? {
        val runnable = app.variants.filter { it.abi == null || it.abi in supported }
        if (runnable.isEmpty()) return null
        val target = installed?.runningAbi?.takeIf { it in supported } ?: supported.firstOrNull()
        val preferredFlavour = app.preferredKind?.substringBefore('@')

        fun score(v: Variant): Int {
            var s = 0
            when {
                v.abi == target -> s += 100
                v.abi == null -> s += 60
                else -> s += 40 - supported.indexOf(v.abi).coerceAtLeast(0)
            }
            // a file signed with the same key as the installed app updates in place and keeps its data
            when (signatureMatch(app, v, installed)) {
                true -> s += 80
                false -> s -= 80
                null -> {}
            }
            if (v.isBundle) s -= 30 // .apks needs a split installer, a plain .apk is easier
            if (preferredFlavour != null && v.kind.substringBefore('@') == preferredFlavour) s += 50
            s -= app.variants.indexOf(v) // keep page order as the last tie-break
            return s
        }

        val best = runnable.maxBy(::score)
        val reasons = buildList {
            when {
                best.abi == target && installed?.runningAbi != null -> add("Same CPU build as your installed copy (${Abi.pretty(target)})")
                best.abi == target -> add("Built for your phone's CPU (${Abi.pretty(target)})")
                best.abi == null -> add("Universal build: runs on any CPU")
                else -> add("Closest build your CPU can run (${Abi.pretty(best.abi)})")
            }
            if (preferredFlavour != null && best.kind.substringBefore('@') == preferredFlavour) add("Your preferred flavour: ${best.labelIn(app.name)}")
            else if (app.variants.size > 1) add("Main build listed first on Farsroid")
            when (signatureMatch(app, best, installed)) {
                true -> add("Same signing key as your installed copy: updates in place and keeps your data")
                false -> add("!Signed with a different key than your installed copy: Android will ask you to uninstall first, and the app's data will be lost")
                null -> if (installed != null && app.signers.isNotEmpty()) add("Signing key not checked yet: Updrop checks it after the download")
            }
            if (best.isBundle) add("!Split bundle (.apks): install it with SAI or a similar split-APK installer")
            if (best.fresh) add("Marked as just updated on the site")
            if (installed != null && app.latestVersion != null) {
                val c = Versions.compare(app.latestVersion, installed.versionName)
                if (c > 0) add("Newer than your ${installed.versionName}")
                else if (c == 0) add("Same version as installed: reinstall only if you want another flavour")
            }
        }
        return Recommendation(best, reasons)
    }

    /** true = same key as installed, false = different key, null = unknown (not installed or never downloaded). */
    fun signatureMatch(app: TrackedApp, v: Variant, installed: Installed?): Boolean? {
        val mine = installed?.signer ?: return null
        val theirs = app.signers[v.flavour] ?: return null
        return mine == theirs
    }
}
