using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("PhonAcq Setup")]
[assembly: AssemblyDescription("PhonAcq 田野语音与方言字表采录系统 安装程序")]
[assembly: AssemblyCompany("KasumiKitsune")]
[assembly: AssemblyProduct("PhonAcq")]
[assembly: AssemblyCopyright("Copyright (c) KasumiKitsune")]

namespace PhonAcqSetup
{
    internal static class NativeMethods
    {
        [DllImport("user32.dll")]
        public static extern bool ReleaseCapture();

        [DllImport("user32.dll")]
        public static extern IntPtr SendMessage(IntPtr hWnd, int msg, int wParam, int lParam);

        [DllImport("user32.dll")]
        public static extern bool SetProcessDPIAware();

        [DllImport("user32.dll")]
        public static extern bool SetProcessDpiAwarenessContext(IntPtr dpiFlag);

        [DllImport("dwmapi.dll")]
        public static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        [DllImport("gdi32.dll")]
        public static extern IntPtr CreateRoundRectRgn(int left, int top, int right, int bottom, int widthEllipse, int heightEllipse);

        [DllImport("shell32.dll")]
        public static extern void SHChangeNotify(int wEventId, uint uFlags, IntPtr dwItem1, IntPtr dwItem2);

        public const int WM_NCLBUTTONDOWN = 0x00A1;
        public const int HTCAPTION = 2;
        public const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        public const int DWMWCP_ROUND = 2;
        public const int SHCNE_ASSOCCHANGED = 0x08000000;
        public const uint SHCNF_IDLIST = 0x0000;

        public static void EnableHighDpi()
        {
            try
            {
                // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = -4
                SetProcessDpiAwarenessContext(new IntPtr(-4));
                return;
            }
            catch
            {
            }
            try
            {
                SetProcessDPIAware();
            }
            catch
            {
            }
        }
    }

    /// <summary>
    /// Modern Windows Vista/10/11 COM IFileOpenDialog (FOS_PICKFOLDERS) so clicking "浏览..."
    /// opens the modern Explorer folder picker instead of the legacy Win95 tree dialog.
    /// </summary>
    internal static class ModernFolderBrowser
    {
        [ComImport]
        [Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
        private class FileOpenDialogRCW { }

        [ComImport]
        [Guid("42f85136-db7e-439c-85f1-e4075d135fc8")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IFileOpenDialog
        {
            [PreserveSig] int Show([In] IntPtr parent);
            void SetFileTypes();
            void SetFileTypeIndex([In] uint iFileType);
            void GetFileTypeIndex(out uint piFileType);
            void Advise();
            void Unadvise([In] uint dwCookie);
            void SetOptions([In] uint fos);
            void GetOptions(out uint pfos);
            void SetDefaultFolder([In, MarshalAs(UnmanagedType.Interface)] IShellItem psi);
            void SetFolder([In, MarshalAs(UnmanagedType.Interface)] IShellItem psi);
            void GetFolder([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
            void GetCurrentSelection([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
            void SetFileName([In, MarshalAs(UnmanagedType.LPWStr)] string pszName);
            void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
            void SetTitle([In, MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
            void SetOkButtonLabel([In, MarshalAs(UnmanagedType.LPWStr)] string pszText);
            void SetFileNameLabel([In, MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
            void GetResult([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
        }

        [ComImport]
        [Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellItem
        {
            void BindToHandler();
            void GetParent();
            void GetDisplayName([In] uint sigdnName, [MarshalAs(UnmanagedType.LPWStr)] out string ppszName);
            void GetAttributes();
            void Compare();
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern int SHCreateItemFromParsingName(
            [MarshalAs(UnmanagedType.LPWStr)] string pszPath,
            IntPtr pbc,
            ref Guid riid,
            [MarshalAs(UnmanagedType.Interface)] out IShellItem ppv);

        private const uint FOS_PICKFOLDERS = 0x00000020;
        private const uint FOS_FORCEFILESYSTEM = 0x00000040;
        private const uint FOS_NOREADONLYRETURN = 0x00008000;
        private const uint SIGDN_FILESYSPATH = 0x80058000;

        public static string PickFolder(IntPtr ownerHandle, string initialDirectory, string title)
        {
            try
            {
                IFileOpenDialog dialog = (IFileOpenDialog)new FileOpenDialogRCW();
                uint options;
                dialog.GetOptions(out options);
                dialog.SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_NOREADONLYRETURN);
                dialog.SetTitle(title);
                dialog.SetOkButtonLabel("选择此位置");

                if (!string.IsNullOrEmpty(initialDirectory))
                {
                    string probe = initialDirectory;
                    while (!string.IsNullOrEmpty(probe) && !Directory.Exists(probe))
                    {
                        probe = Path.GetDirectoryName(probe);
                    }
                    if (!string.IsNullOrEmpty(probe) && Directory.Exists(probe))
                    {
                        Guid shellItemGuid = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
                        IShellItem folderItem;
                        if (SHCreateItemFromParsingName(probe, IntPtr.Zero, ref shellItemGuid, out folderItem) == 0 && folderItem != null)
                        {
                            dialog.SetFolder(folderItem);
                        }
                    }
                }

                int hr = dialog.Show(ownerHandle);
                if (hr == 0)
                {
                    IShellItem resultItem;
                    dialog.GetResult(out resultItem);
                    if (resultItem != null)
                    {
                        string selectedPath;
                        resultItem.GetDisplayName(SIGDN_FILESYSPATH, out selectedPath);
                        if (!string.IsNullOrEmpty(selectedPath))
                        {
                            return selectedPath;
                        }
                    }
                }
                return null;
            }
            catch
            {
                using (FolderBrowserDialog fallback = new FolderBrowserDialog())
                {
                    fallback.Description = title;
                    if (!string.IsNullOrEmpty(initialDirectory) && Directory.Exists(initialDirectory))
                    {
                        fallback.SelectedPath = initialDirectory;
                    }
                    if (fallback.ShowDialog() == DialogResult.OK)
                    {
                        return fallback.SelectedPath;
                    }
                }
                return null;
            }
        }
    }

    /// <summary>
    /// Read-only Seekable window over the appended ZIP payload inside the self-extracting setup .exe.
    /// </summary>
    internal sealed class EmbeddedPayloadStream : Stream
    {
        private readonly FileStream _baseStream;
        private readonly long _offset;
        private readonly long _length;
        private long _position;

        public EmbeddedPayloadStream(FileStream baseStream, long offset, long length)
        {
            _baseStream = baseStream;
            _offset = offset;
            _length = length;
            _position = 0;
            _baseStream.Seek(_offset, SeekOrigin.Begin);
        }

        public override bool CanRead { get { return true; } }
        public override bool CanSeek { get { return true; } }
        public override bool CanWrite { get { return false; } }
        public override long Length { get { return _length; } }

        public override long Position
        {
            get { return _position; }
            set { Seek(value, SeekOrigin.Begin); }
        }

        public override void Flush() { }

        public override int Read(byte[] buffer, int offset, int count)
        {
            long remaining = _length - _position;
            if (remaining <= 0) return 0;
            if (count > remaining) count = (int)remaining;
            _baseStream.Seek(_offset + _position, SeekOrigin.Begin);
            int read = _baseStream.Read(buffer, offset, count);
            _position += read;
            return read;
        }

        public override long Seek(long offset, SeekOrigin origin)
        {
            long nextPos;
            switch (origin)
            {
                case SeekOrigin.Begin:
                    nextPos = offset;
                    break;
                case SeekOrigin.Current:
                    nextPos = _position + offset;
                    break;
                case SeekOrigin.End:
                    nextPos = _length + offset;
                    break;
                default:
                    throw new ArgumentOutOfRangeException("origin");
            }
            if (nextPos < 0) nextPos = 0;
            if (nextPos > _length) nextPos = _length;
            _position = nextPos;
            _baseStream.Seek(_offset + _position, SeekOrigin.Begin);
            return _position;
        }

        public override void SetLength(long value) { throw new NotSupportedException(); }
        public override void Write(byte[] buffer, int offset, int count) { throw new NotSupportedException(); }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                _baseStream.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    internal sealed class PayloadDescriptor
    {
        public long PayloadOffset;
        public long PayloadLength;
        public long StubLength;
        public string AppVersion;
    }

    internal static class InstallerEngine
    {
        public const string APP_NAME = "PhonAcq";
        public const string PUBLISHER = "KasumiKitsune";
        public const string UNINSTALL_REG_SUBKEY = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\PhonAcq";
        public static readonly byte[] TRAILER_MAGIC = Encoding.ASCII.GetBytes("PAQSETUP");
        // Trailer layout (32 bytes at EOF):
        // [0..7]   "PAQSETUP" (8 bytes)
        // [8..15]  payloadOffset (Int64 LE)
        // [16..23] payloadLength (Int64 LE)
        // [24..31] stubLength    (Int64 LE)

        public static PayloadDescriptor ReadPayloadDescriptor(string exePath, string fallbackVersion)
        {
            FileInfo fi = new FileInfo(exePath);
            if (!fi.Exists || fi.Length < 64) return null;

            using (FileStream fs = new FileStream(exePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            {
                fs.Seek(-32, SeekOrigin.End);
                byte[] trailer = new byte[32];
                int read = fs.Read(trailer, 0, 32);
                if (read != 32) return null;

                for (int i = 0; i < 8; i++)
                {
                    if (trailer[i] != TRAILER_MAGIC[i]) return null;
                }

                long payloadOffset = BitConverter.ToInt64(trailer, 8);
                long payloadLength = BitConverter.ToInt64(trailer, 16);
                long stubLength = BitConverter.ToInt64(trailer, 24);
                if (payloadOffset <= 0 || payloadLength <= 0 || payloadOffset + payloadLength + 32 > fi.Length)
                {
                    return null;
                }

                PayloadDescriptor desc = new PayloadDescriptor();
                desc.PayloadOffset = payloadOffset;
                desc.PayloadLength = payloadLength;
                desc.StubLength = stubLength > 0 ? stubLength : payloadOffset;
                desc.AppVersion = fallbackVersion;
                return desc;
            }
        }

        public static void DetectExistingInstallation(
            out string existingDir,
            out string existingVersion,
            out List<string> legacyMsiProductCodes)
        {
            existingDir = null;
            existingVersion = null;
            legacyMsiProductCodes = new List<string>();

            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UNINSTALL_REG_SUBKEY))
                {
                    if (key != null)
                    {
                        string loc = key.GetValue("InstallLocation") as string;
                        string ver = key.GetValue("DisplayVersion") as string;
                        if (!string.IsNullOrEmpty(loc)) existingDir = loc;
                        if (!string.IsNullOrEmpty(ver)) existingVersion = ver;
                    }
                }
            }
            catch
            {
            }

            // Also scan HKCU Uninstall for legacy jpackage/WiX MSI entries of PhonAcq so same-version upgrades never conflict
            try
            {
                using (RegistryKey uninstallRoot = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall"))
                {
                    if (uninstallRoot != null)
                    {
                        foreach (string subName in uninstallRoot.GetSubKeyNames())
                        {
                            if (string.Equals(subName, APP_NAME, StringComparison.OrdinalIgnoreCase)) continue;
                            using (RegistryKey sub = uninstallRoot.OpenSubKey(subName))
                            {
                                if (sub == null) continue;
                                string dispName = sub.GetValue("DisplayName") as string;
                                if (!string.Equals(dispName, APP_NAME, StringComparison.OrdinalIgnoreCase)) continue;

                                string uninst = sub.GetValue("UninstallString") as string;
                                string loc = sub.GetValue("InstallLocation") as string;
                                string ver = sub.GetValue("DisplayVersion") as string;

                                if (string.IsNullOrEmpty(existingVersion) && !string.IsNullOrEmpty(ver))
                                {
                                    existingVersion = ver;
                                }
                                if (string.IsNullOrEmpty(existingDir) && !string.IsNullOrEmpty(loc))
                                {
                                    existingDir = loc.TrimEnd('\\');
                                }

                                if (!string.IsNullOrEmpty(uninst) &&
                                    uninst.IndexOf("MsiExec", StringComparison.OrdinalIgnoreCase) >= 0 &&
                                    subName.StartsWith("{") && subName.EndsWith("}"))
                                {
                                    legacyMsiProductCodes.Add(subName);
                                }
                            }
                        }
                    }
                }
            }
            catch
            {
            }

            // Check legacy jpackage per-user default location (%LOCALAPPDATA%\PhonAcq)
            if (string.IsNullOrEmpty(existingDir))
            {
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string modernDefault = Path.Combine(localAppData, "Programs", APP_NAME);
                string legacyMsiDefault = Path.Combine(localAppData, APP_NAME);

                if (File.Exists(Path.Combine(modernDefault, "PhonAcq.exe")))
                {
                    existingDir = modernDefault;
                }
                else if (File.Exists(Path.Combine(legacyMsiDefault, "PhonAcq.exe")))
                {
                    existingDir = legacyMsiDefault;
                }
            }
        }

        public static string GetDefaultInstallDir(string detectedDir)
        {
            if (!string.IsNullOrEmpty(detectedDir))
            {
                return detectedDir;
            }
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localAppData, "Programs", APP_NAME);
        }

        public static void StopRunningPhonAcqProcesses()
        {
            int selfPid = Process.GetCurrentProcess().Id;
            try
            {
                Process[] procs = Process.GetProcessesByName("PhonAcq");
                foreach (Process p in procs)
                {
                    try
                    {
                        if (p.Id == selfPid) continue;
                        if (p.CloseMainWindow())
                        {
                            if (p.WaitForExit(2200)) continue;
                        }
                        if (!p.HasExited)
                        {
                            p.Kill();
                            p.WaitForExit(2000);
                        }
                    }
                    catch
                    {
                    }
                    finally
                    {
                        p.Dispose();
                    }
                }
            }
            catch
            {
            }
        }

        public static void CleanupLegacyPerUserMsi(List<string> productCodes)
        {
            if (productCodes == null || productCodes.Count == 0) return;
            foreach (string code in productCodes)
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = "msiexec.exe";
                    psi.Arguments = String.Format("/x {0} /qn /norestart", code);
                    psi.CreateNoWindow = true;
                    psi.UseShellExecute = false;
                    using (Process p = Process.Start(psi))
                    {
                        if (p != null) p.WaitForExit(15000);
                    }
                }
                catch
                {
                }
            }
        }

        public static void CreateShortcut(string shortcutPath, string targetExe, string workingDir, string description)
        {
            try
            {
                string parent = Path.GetDirectoryName(shortcutPath);
                if (!string.IsNullOrEmpty(parent) && !Directory.Exists(parent))
                {
                    Directory.CreateDirectory(parent);
                }

                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                object shell = Activator.CreateInstance(shellType);
                try
                {
                    object shortcut = shellType.InvokeMember(
                        "CreateShortcut",
                        BindingFlags.InvokeMethod,
                        null,
                        shell,
                        new object[] { shortcutPath });
                    if (shortcut == null) return;
                    try
                    {
                        Type scType = shortcut.GetType();
                        scType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe });
                        scType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDir });
                        scType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe + ",0" });
                        scType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
                        scType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
                    }
                    finally
                    {
                        Marshal.FinalReleaseComObject(shortcut);
                    }
                }
                finally
                {
                    Marshal.FinalReleaseComObject(shell);
                }
            }
            catch
            {
            }
        }

        public static void RegisterFileAssociation(string targetExe)
        {
            try
            {
                using (RegistryKey extKey = Registry.CurrentUser.CreateSubKey(@"Software\Classes\.teproj"))
                {
                    if (extKey != null) extKey.SetValue("", "PhonAcq.teproj");
                }
                using (RegistryKey progKey = Registry.CurrentUser.CreateSubKey(@"Software\Classes\PhonAcq.teproj"))
                {
                    if (progKey != null)
                    {
                        progKey.SetValue("", "PhonAcq 语音调查工程包 (.teproj)");
                        using (RegistryKey iconKey = progKey.CreateSubKey("DefaultIcon"))
                        {
                            if (iconKey != null) iconKey.SetValue("", String.Format("\"{0}\",0", targetExe));
                        }
                        using (RegistryKey cmdKey = progKey.CreateSubKey(@"shell\open\command"))
                        {
                            if (cmdKey != null) cmdKey.SetValue("", String.Format("\"{0}\" \"%1\"", targetExe));
                        }
                    }
                }
                NativeMethods.SHChangeNotify(NativeMethods.SHCNE_ASSOCCHANGED, NativeMethods.SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
            }
            catch
            {
            }
        }

        public static void RegisterUninstaller(string installDir, string targetExe, string uninstallerExe, string version, long totalBytes)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_REG_SUBKEY))
                {
                    if (key == null) return;
                    key.SetValue("DisplayName", APP_NAME);
                    key.SetValue("DisplayVersion", version);
                    key.SetValue("Publisher", PUBLISHER);
                    key.SetValue("InstallLocation", installDir);
                    key.SetValue("DisplayIcon", targetExe);
                    key.SetValue("UninstallString", String.Format("\"{0}\" --uninstall", uninstallerExe));
                    key.SetValue("QuietUninstallString", String.Format("\"{0}\" --uninstall --silent", uninstallerExe));
                    key.SetValue("InstallDate", DateTime.Now.ToString("yyyyMMdd"));
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                    int sizeKb = (int)Math.Max(1024L, totalBytes / 1024L);
                    key.SetValue("EstimatedSize", sizeKb, RegistryValueKind.DWord);
                }
            }
            catch
            {
            }
        }

        public static void PerformInstall(
            string setupExePath,
            PayloadDescriptor desc,
            string installDir,
            bool createDesktopShortcut,
            bool createStartMenuShortcut,
            bool associateTeproj,
            List<string> legacyMsiCodes,
            Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(3, "正在检测并关闭运行中的 PhonAcq 进程...");
            StopRunningPhonAcqProcesses();

            if (legacyMsiCodes != null && legacyMsiCodes.Count > 0)
            {
                if (onProgress != null) onProgress(8, "正在清理旧版 MSI 安装记录（保留本地录音与语料数据）...");
                CleanupLegacyPerUserMsi(legacyMsiCodes);
            }

            if (!Directory.Exists(installDir))
            {
                Directory.CreateDirectory(installDir);
            }

            // Clean old runtime/app jars before extracting so stale jars from older versions don't linger
            string oldAppSubdir = Path.Combine(installDir, "app");
            string oldRuntimeSubdir = Path.Combine(installDir, "runtime");
            try
            {
                if (Directory.Exists(oldAppSubdir)) Directory.Delete(oldAppSubdir, true);
                if (Directory.Exists(oldRuntimeSubdir)) Directory.Delete(oldRuntimeSubdir, true);
            }
            catch
            {
            }

            long totalExtractedBytes = 0;
            using (FileStream fs = new FileStream(setupExePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (EmbeddedPayloadStream sub = new EmbeddedPayloadStream(fs, desc.PayloadOffset, desc.PayloadLength))
            using (ZipArchive archive = new ZipArchive(sub, ZipArchiveMode.Read))
            {
                int totalEntries = Math.Max(1, archive.Entries.Count);
                int index = 0;
                byte[] copyBuf = new byte[81920];

                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    index++;
                    string relPath = entry.FullName.Replace('/', Path.DirectorySeparatorChar);
                    if (string.IsNullOrEmpty(relPath)) continue;

                    string fullDest = Path.GetFullPath(Path.Combine(installDir, relPath));
                    string rootPrefix = Path.GetFullPath(installDir);
                    if (!rootPrefix.EndsWith(Path.DirectorySeparatorChar.ToString()))
                    {
                        rootPrefix += Path.DirectorySeparatorChar;
                    }
                    if (!fullDest.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }

                    if (string.IsNullOrEmpty(entry.Name) && (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\")))
                    {
                        if (!Directory.Exists(fullDest)) Directory.CreateDirectory(fullDest);
                        continue;
                    }

                    string parentDir = Path.GetDirectoryName(fullDest);
                    if (!string.IsNullOrEmpty(parentDir) && !Directory.Exists(parentDir))
                    {
                        Directory.CreateDirectory(parentDir);
                    }

                    using (Stream inStream = entry.Open())
                    using (FileStream outStream = new FileStream(fullDest, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        int r;
                        while ((r = inStream.Read(copyBuf, 0, copyBuf.Length)) > 0)
                        {
                            outStream.Write(copyBuf, 0, r);
                            totalExtractedBytes += r;
                        }
                    }

                    int pct = 12 + (int)((index * 78L) / totalEntries);
                    if (index == 1 || index % 8 == 0 || index == totalEntries)
                    {
                        if (onProgress != null)
                        {
                            onProgress(pct, String.Format("正在释放 PhonAcq 程序与声学引擎 ({0}/{1})...", index, totalEntries));
                        }
                    }
                }
            }

            if (onProgress != null) onProgress(93, "正在写入卸载向导与系统注册信息...");

            string uninstallerExe = Path.Combine(installDir, "Uninstall-PhonAcq.exe");
            using (FileStream srcStub = new FileStream(setupExePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (FileStream dstStub = new FileStream(uninstallerExe, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                long remaining = desc.StubLength;
                byte[] buf = new byte[65536];
                while (remaining > 0)
                {
                    int chunk = (int)Math.Min(buf.Length, remaining);
                    int n = srcStub.Read(buf, 0, chunk);
                    if (n <= 0) break;
                    dstStub.Write(buf, 0, n);
                    remaining -= n;
                }
            }

            string mainExe = Path.Combine(installDir, "PhonAcq.exe");
            RegisterUninstaller(installDir, mainExe, uninstallerExe, desc.AppVersion, totalExtractedBytes);

            if (onProgress != null) onProgress(97, "正在配置快捷方式与文件关联...");

            string descText = "PhonAcq 田野语音与方言字表采录系统";
            if (createDesktopShortcut)
            {
                string desktopDir = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                CreateShortcut(Path.Combine(desktopDir, "PhonAcq.lnk"), mainExe, installDir, descText);
            }
            if (createStartMenuShortcut)
            {
                string programsDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                string groupDir = Path.Combine(programsDir, APP_NAME);
                CreateShortcut(Path.Combine(groupDir, "PhonAcq.lnk"), mainExe, installDir, descText);
            }
            if (associateTeproj)
            {
                RegisterFileAssociation(mainExe);
            }

            if (onProgress != null) onProgress(100, "PhonAcq 安装完成！");
        }

        public static void PerformUninstall(string installDir, string selfExePath, Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(15, "正在关闭运行中的 PhonAcq 进程...");
            StopRunningPhonAcqProcesses();

            if (onProgress != null) onProgress(45, "正在清理桌面与开始菜单快捷方式...");
            try
            {
                string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "PhonAcq.lnk");
                if (File.Exists(desktopLnk)) File.Delete(desktopLnk);

                string startMenuGroup = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), APP_NAME);
                if (Directory.Exists(startMenuGroup)) Directory.Delete(startMenuGroup, true);
            }
            catch
            {
            }

            if (onProgress != null) onProgress(68, "正在清理注册表与文件关联...");
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(UNINSTALL_REG_SUBKEY, false);
                Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\PhonAcq.teproj", false);
                using (RegistryKey extKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\.teproj", true))
                {
                    if (extKey != null)
                    {
                        string cur = extKey.GetValue("") as string;
                        if (string.Equals(cur, "PhonAcq.teproj", StringComparison.OrdinalIgnoreCase))
                        {
                            Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\.teproj", false);
                        }
                    }
                }
                NativeMethods.SHChangeNotify(NativeMethods.SHCNE_ASSOCCHANGED, NativeMethods.SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
            }
            catch
            {
            }

            if (onProgress != null) onProgress(88, "正在移除程序运行目录...");
            if (!string.IsNullOrEmpty(installDir) && Directory.Exists(installDir))
            {
                foreach (string subDir in Directory.GetDirectories(installDir))
                {
                    try { Directory.Delete(subDir, true); } catch { }
                }
                foreach (string file in Directory.GetFiles(installDir))
                {
                    if (string.Equals(Path.GetFullPath(file), Path.GetFullPath(selfExePath), StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }
                    try { File.Delete(file); } catch { }
                }

                // Schedule background self-removal of Uninstall-PhonAcq.exe & empty install directory after exit
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = "cmd.exe";
                    psi.Arguments = String.Format("/c ping 127.0.0.1 -n 2 > nul & del /f /q \"{0}\" & rmdir \"{1}\"", selfExePath, installDir);
                    psi.CreateNoWindow = true;
                    psi.WindowStyle = ProcessWindowStyle.Hidden;
                    psi.UseShellExecute = false;
                    Process.Start(psi);
                }
                catch
                {
                }
            }

            if (onProgress != null) onProgress(100, "PhonAcq 已成功卸载。");
        }
    }

    internal enum SetupStage
    {
        Ready,
        Working,
        Completed,
        Error
    }

    internal sealed class PhonAcqInstallerForm : Form
    {
        private readonly bool _isUninstallMode;
        private readonly string _appVersion;
        private readonly PayloadDescriptor _payload;
        private readonly Image _logoImage;

        private string _installDir;
        private readonly string _existingVersion;
        private readonly bool _isOverwriteUpgrade;
        private readonly List<string> _legacyMsiCodes;

        private SetupStage _stage = SetupStage.Ready;
        private bool _optionsExpanded = false;
        private bool _optDesktopShortcut = true;
        private bool _optStartMenuShortcut = true;
        private bool _optAssociateTeproj = true;

        private int _progressPercent = 0;
        private string _statusMessage = "";
        private string _errorMessage = "";

        private float _dpiScale = 1.0f;
        private TextBox _pathTextBox;

        // Interactive hit rectangles (in client pixels)
        private Rectangle _rectMinBtn;
        private Rectangle _rectCloseBtn;
        private Rectangle _rectPrimaryBtn;
        private Rectangle _rectSecondaryBtn;
        private Rectangle _rectExpandToggle;
        private Rectangle _rectBrowseBtn;
        private Rectangle _rectChkDesktop;
        private Rectangle _rectChkStartMenu;
        private Rectangle _rectChkTeproj;

        private string _hoveredId = null;

        public PhonAcqInstallerForm(bool isUninstallMode, string appVersion, PayloadDescriptor payload)
        {
            _isUninstallMode = isUninstallMode;
            _appVersion = appVersion;
            _payload = payload;

            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.UserPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.ResizeRedraw,
                true);

            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(248, 250, 252);
            Text = isUninstallMode ? "PhonAcq 卸载向导" : "PhonAcq 安装程序";

            try
            {
                Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch
            {
            }

            _logoImage = LoadEmbeddedLogo();

            string detectedDir;
            string detectedVer;
            List<string> msiCodes;
            InstallerEngine.DetectExistingInstallation(out detectedDir, out detectedVer, out msiCodes);
            _existingVersion = detectedVer;
            _legacyMsiCodes = msiCodes;

            if (_isUninstallMode)
            {
                string exeDir = Path.GetDirectoryName(Application.ExecutablePath);
                _installDir = !string.IsNullOrEmpty(detectedDir) ? detectedDir : exeDir;
                _isOverwriteUpgrade = false;
            }
            else
            {
                _installDir = InstallerEngine.GetDefaultInstallDir(detectedDir);
                _isOverwriteUpgrade = !string.IsNullOrEmpty(detectedDir) ||
                                      !string.IsNullOrEmpty(detectedVer) ||
                                      (_legacyMsiCodes != null && _legacyMsiCodes.Count > 0);
            }

            _pathTextBox = new TextBox();
            _pathTextBox.BorderStyle = BorderStyle.None;
            _pathTextBox.BackColor = Color.White;
            _pathTextBox.ForeColor = Color.FromArgb(30, 41, 59);
            _pathTextBox.Text = _installDir;
            _pathTextBox.Visible = false;
            _pathTextBox.TextChanged += delegate { _installDir = _pathTextBox.Text.Trim(); };
            Controls.Add(_pathTextBox);
        }

        private static Image LoadEmbeddedLogo()
        {
            try
            {
                Assembly asm = Assembly.GetExecutingAssembly();
                foreach (string resName in asm.GetManifestResourceNames())
                {
                    if (resName.EndsWith("phonacq-logo.png", StringComparison.OrdinalIgnoreCase))
                    {
                        Stream s = asm.GetManifestResourceStream(resName);
                        if (s != null) return Image.FromStream(s);
                    }
                }
            }
            catch
            {
            }
            return null;
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            using (Graphics g = CreateGraphics())
            {
                _dpiScale = Math.Max(1.0f, g.DpiX / 96.0f);
            }
            ApplyWindowGeometry();
        }

        private int S(float logicalPx)
        {
            return (int)Math.Round(logicalPx * _dpiScale);
        }

        private void ApplyWindowGeometry()
        {
            int w = S(540);
            int h = (!_isUninstallMode && _stage == SetupStage.Ready && _optionsExpanded) ? S(504) : S(374);
            ClientSize = new Size(w, h);

            // Request Windows 11 native DWM rounded corners + shadow; fall back to round region on Windows 10
            int cornerPref = NativeMethods.DWMWCP_ROUND;
            int hr = -1;
            try
            {
                hr = NativeMethods.DwmSetWindowAttribute(
                    Handle,
                    NativeMethods.DWMWA_WINDOW_CORNER_PREFERENCE,
                    ref cornerPref,
                    sizeof(int));
            }
            catch
            {
            }
            if (hr != 0)
            {
                IntPtr rgn = NativeMethods.CreateRoundRectRgn(0, 0, w + 1, h + 1, S(20), S(20));
                Region = Region.FromHrgn(rgn);
            }

            LayoutPathTextBox();
            Invalidate();
        }

        private void LayoutPathTextBox()
        {
            bool showPath = !_isUninstallMode && _stage == SetupStage.Ready && _optionsExpanded;
            _pathTextBox.Visible = showPath;
            if (showPath)
            {
                _pathTextBox.Font = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Regular, GraphicsUnit.Point);
                int boxX = S(36);
                int boxY = S(396);
                int boxW = ClientSize.Width - S(142);
                _pathTextBox.Location = new Point(boxX + S(10), boxY + (S(34) - _pathTextBox.PreferredHeight) / 2);
                _pathTextBox.Width = boxW - S(20);
            }
        }

        private static GraphicsPath CreateRoundRect(Rectangle r, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            if (d <= 0)
            {
                path.AddRectangle(r);
                return path;
            }
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

            int w = ClientSize.Width;
            int h = ClientSize.Height;

            // Surface background
            using (SolidBrush bgBrush = new SolidBrush(Color.FromArgb(248, 250, 252)))
            {
                g.FillRectangle(bgBrush, 0, 0, w, h);
            }

            // 1. Studio Acoustic Hero Header
            int headerH = S(148);
            Rectangle headerRect = new Rectangle(0, 0, w, headerH);
            using (LinearGradientBrush heroGrad = new LinearGradientBrush(
                headerRect,
                Color.FromArgb(12, 26, 46),
                Color.FromArgb(11, 93, 118),
                24f))
            {
                g.FillRectangle(heroGrad, headerRect);
            }

            // Decorative subtle acoustic F0 & formant wave ribbons inside header
            DrawDecorativeAcousticWaves(g, w, headerH);

            // Top-right custom Minimize & Close buttons (smooth alpha highlight, zero gray flash)
            _rectCloseBtn = new Rectangle(w - S(42), S(12), S(30), S(28));
            _rectMinBtn = new Rectangle(w - S(76), S(12), S(30), S(28));

            bool closeEnabled = _stage != SetupStage.Working;
            if (_hoveredId == "close" && closeEnabled)
            {
                using (GraphicsPath cp = CreateRoundRect(_rectCloseBtn, S(7)))
                using (SolidBrush cb = new SolidBrush(Color.FromArgb(220, 38, 38)))
                {
                    g.FillPath(cb, cp);
                }
            }
            if (_hoveredId == "min")
            {
                using (GraphicsPath mp = CreateRoundRect(_rectMinBtn, S(7)))
                using (SolidBrush mb = new SolidBrush(Color.FromArgb(42, 255, 255, 255)))
                {
                    g.FillPath(mb, mp);
                }
            }

            using (Pen winPen = new Pen(Color.FromArgb(closeEnabled ? 230 : 95, 255, 255, 255), Math.Max(1.4f, 1.5f * _dpiScale)))
            {
                winPen.StartCap = LineCap.Round;
                winPen.EndCap = LineCap.Round;
                // Minimize bar
                int my = _rectMinBtn.Y + _rectMinBtn.Height / 2;
                g.DrawLine(winPen, _rectMinBtn.X + S(9), my, _rectMinBtn.Right - S(9), my);
                // Close X
                int cx1 = _rectCloseBtn.X + S(10);
                int cy1 = _rectCloseBtn.Y + S(9);
                int cx2 = _rectCloseBtn.Right - S(10);
                int cy2 = _rectCloseBtn.Bottom - S(9);
                g.DrawLine(winPen, cx1, cy1, cx2, cy2);
                g.DrawLine(winPen, cx2, cy1, cx1, cy2);
            }

            // Brand Pebble Logo + Title + Version Badge
            int logoSize = S(72);
            int logoX = S(34);
            int logoY = (headerH - logoSize) / 2 + S(4);
            if (_logoImage != null)
            {
                g.DrawImage(_logoImage, new Rectangle(logoX, logoY, logoSize, logoSize));
            }
            else
            {
                using (GraphicsPath p = CreateRoundRect(new Rectangle(logoX, logoY, logoSize, logoSize), S(20)))
                using (SolidBrush wb = new SolidBrush(Color.White))
                {
                    g.FillPath(wb, p);
                }
            }

            int textLeft = logoX + logoSize + S(18);
            using (Font titleFont = new Font("Segoe UI", 21f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush whiteBrush = new SolidBrush(Color.White))
            {
                g.DrawString("PhonAcq", titleFont, whiteBrush, textLeft - S(2), logoY + S(3));
                SizeF titleSz = g.MeasureString("PhonAcq", titleFont);

                // Version Pill next to "PhonAcq"
                string badgeText = _isUninstallMode
                    ? "卸载向导"
                    : String.Format("v{0} · Windows x64", _appVersion);
                using (Font badgeFont = new Font("Segoe UI Semibold", 8.5f, FontStyle.Bold, GraphicsUnit.Point))
                {
                    SizeF bSz = g.MeasureString(badgeText, badgeFont);
                    Rectangle pillRect = new Rectangle(
                        textLeft + (int)titleSz.Width + S(4),
                        logoY + S(13),
                        (int)bSz.Width + S(14),
                        (int)bSz.Height + S(5));
                    using (GraphicsPath pillPath = CreateRoundRect(pillRect, pillRect.Height / 2))
                    using (SolidBrush pillFill = new SolidBrush(Color.FromArgb(48, 56, 189, 248)))
                    using (Pen pillBorder = new Pen(Color.FromArgb(115, 125, 211, 252), 1f))
                    using (SolidBrush pillTextBrush = new SolidBrush(Color.FromArgb(224, 242, 254)))
                    {
                        g.FillPath(pillFill, pillPath);
                        g.DrawPath(pillBorder, pillPath);
                        g.DrawString(badgeText, badgeFont, pillTextBrush, pillRect.X + S(7), pillRect.Y + S(2.5f));
                    }
                }
            }

            using (Font subFont = new Font("Microsoft YaHei UI", 9.8f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush subBrush = new SolidBrush(Color.FromArgb(215, 226, 238, 248)))
            {
                string subtitle = _isUninstallMode
                    ? "安全移除 PhonAcq 桌面端程序组件"
                    : "田野语音与方言字表采录系统 · 内置独立运行环境";
                g.DrawString(subtitle, subFont, subBrush, textLeft, logoY + S(44));
            }

            // 2. Body Content Area depending on Stage
            _rectPrimaryBtn = Rectangle.Empty;
            _rectSecondaryBtn = Rectangle.Empty;
            _rectExpandToggle = Rectangle.Empty;
            _rectBrowseBtn = Rectangle.Empty;
            _rectChkDesktop = Rectangle.Empty;
            _rectChkStartMenu = Rectangle.Empty;
            _rectChkTeproj = Rectangle.Empty;

            if (_stage == SetupStage.Ready)
            {
                if (_isUninstallMode)
                {
                    DrawUninstallReadyBody(g, w, headerH);
                }
                else
                {
                    DrawInstallReadyBody(g, w, headerH);
                }
            }
            else if (_stage == SetupStage.Working)
            {
                DrawWorkingProgressBody(g, w, headerH);
            }
            else if (_stage == SetupStage.Completed)
            {
                DrawCompletedBody(g, w, headerH);
            }
            else if (_stage == SetupStage.Error)
            {
                DrawErrorBody(g, w, headerH);
            }

            // Subtle crisp outer card border
            using (Pen borderPen = new Pen(Color.FromArgb(203, 213, 225), 1f))
            {
                g.DrawRectangle(borderPen, 0, 0, w - 1, h - 1);
            }
        }

        private void DrawDecorativeAcousticWaves(Graphics g, int w, int headerH)
        {
            using (GraphicsPath wave1 = new GraphicsPath())
            using (GraphicsPath wave2 = new GraphicsPath())
            {
                PointF[] pts1 = new PointF[7];
                PointF[] pts2 = new PointF[7];
                for (int i = 0; i < 7; i++)
                {
                    float x = (w * i) / 6.0f;
                    float y1 = headerH * 0.72f + (float)Math.Sin(i * 0.95 + 0.4) * S(16);
                    float y2 = headerH * 0.80f + (float)Math.Cos(i * 1.10 + 0.8) * S(12);
                    pts1[i] = new PointF(x, y1);
                    pts2[i] = new PointF(x, y2);
                }
                wave1.AddCurve(pts1, 0.45f);
                wave2.AddCurve(pts2, 0.45f);

                using (Pen p1 = new Pen(Color.FromArgb(28, 56, 189, 248), Math.Max(1.5f, 1.8f * _dpiScale)))
                using (Pen p2 = new Pen(Color.FromArgb(26, 245, 158, 11), Math.Max(1.2f, 1.5f * _dpiScale)))
                {
                    g.DrawPath(p1, wave1);
                    g.DrawPath(p2, wave2);
                }
            }
        }

        private void DrawInstallReadyBody(Graphics g, int w, int headerH)
        {
            int contentX = S(36);
            int contentW = w - S(72);

            // Status Banner (Same-version / Upgrade detection vs Clean Install)
            Rectangle bannerRect = new Rectangle(contentX, headerH + S(24), contentW, S(46));
            Color bannerFill = _isOverwriteUpgrade ? Color.FromArgb(236, 253, 245) : Color.FromArgb(240, 249, 255);
            Color bannerBorder = _isOverwriteUpgrade ? Color.FromArgb(167, 243, 208) : Color.FromArgb(186, 230, 253);
            Color bannerTextCol = _isOverwriteUpgrade ? Color.FromArgb(6, 95, 70) : Color.FromArgb(12, 74, 110);

            using (GraphicsPath bp = CreateRoundRect(bannerRect, S(11)))
            using (SolidBrush bf = new SolidBrush(bannerFill))
            using (Pen bPen = new Pen(bannerBorder, 1f))
            using (Font bFont = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush bt = new SolidBrush(bannerTextCol))
            {
                g.FillPath(bf, bp);
                g.DrawPath(bPen, bp);

                string note = _isOverwriteUpgrade
                    ? String.Format(
                        "✓ 已检测到本机安装的 PhonAcq{0}，支持同版本/跨版本直接覆盖更新，全部录音与字表数据完整保留。",
                        !string.IsNullOrEmpty(_existingVersion) ? (" (v" + _existingVersion + ")") : "")
                    : "✓ 无需预先安装 Java 或额外组件 · 一键部署声学分析、双机互联与无损字表采录工作台。";

                RectangleF textLayout = new RectangleF(
                    bannerRect.X + S(14),
                    bannerRect.Y + S(6),
                    bannerRect.Width - S(28),
                    bannerRect.Height - S(10));
                StringFormat sf = new StringFormat();
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(note, bFont, bt, textLayout, sf);
            }

            // Big Primary One-Click Action Button
            int btnW = S(268);
            int btnH = S(50);
            _rectPrimaryBtn = new Rectangle((w - btnW) / 2, headerH + S(92), btnW, btnH);
            string ctaLabel = _isOverwriteUpgrade ? "覆盖安装 PhonAcq" : "立即安装";
            DrawPrimaryActionButton(g, _rectPrimaryBtn, ctaLabel, _hoveredId == "primary", false);

            // Expandable Options Toggle Link
            string toggleLabel = _optionsExpanded ? "收起自定义选项 ▴" : "自定义安装位置与快捷方式 ▾";
            using (Font tFont = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Regular, GraphicsUnit.Point))
            {
                SizeF tSz = g.MeasureString(toggleLabel, tFont);
                int tw = (int)tSz.Width + S(16);
                int th = (int)tSz.Height + S(8);
                _rectExpandToggle = new Rectangle((w - tw) / 2, headerH + S(158), tw, th);

                Color tCol = _hoveredId == "toggle"
                    ? Color.FromArgb(2, 132, 199)
                    : Color.FromArgb(100, 116, 139);
                using (SolidBrush tb = new SolidBrush(tCol))
                {
                    g.DrawString(toggleLabel, tFont, tb, _rectExpandToggle.X + S(8), _rectExpandToggle.Y + S(4));
                }
            }

            if (!_optionsExpanded)
            {
                // Subtle current path preview at bottom
                using (Font pFont = new Font("Microsoft YaHei UI", 8.2f, FontStyle.Regular, GraphicsUnit.Point))
                using (SolidBrush pb = new SolidBrush(Color.FromArgb(148, 163, 184)))
                {
                    StringFormat csf = new StringFormat();
                    csf.Alignment = StringAlignment.Center;
                    csf.Trimming = StringTrimming.EllipsisPath;
                    csf.FormatFlags = StringFormatFlags.NoWrap;
                    RectangleF pr = new RectangleF(S(36), headerH + S(192), w - S(72), S(20));
                    g.DrawString("默认位置：" + _installDir, pFont, pb, pr, csf);
                }
                return;
            }

            // Expanded Custom Drawer Panel
            int drawerY = S(350);
            int drawerH = S(132);
            Rectangle drawerRect = new Rectangle(contentX - S(6), drawerY, contentW + S(12), drawerH);
            using (GraphicsPath dp = CreateRoundRect(drawerRect, S(12)))
            using (SolidBrush df = new SolidBrush(Color.FromArgb(241, 245, 249)))
            using (Pen dPen = new Pen(Color.FromArgb(226, 232, 240), 1f))
            {
                g.FillPath(df, dp);
                g.DrawPath(dPen, dp);
            }

            using (Font lblFont = new Font("Microsoft YaHei UI", 8.8f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush lblBrush = new SolidBrush(Color.FromArgb(71, 85, 105)))
            {
                g.DrawString("安装目录", lblFont, lblBrush, contentX + S(4), drawerY + S(10));
            }

            // Path Input Pill + Browse Button
            int inputY = S(396);
            int inputW = w - S(142);
            Rectangle inputBoxRect = new Rectangle(contentX, inputY, inputW, S(34));
            using (GraphicsPath ip = CreateRoundRect(inputBoxRect, S(8)))
            using (SolidBrush ib = new SolidBrush(Color.White))
            using (Pen iPen = new Pen(
                _pathTextBox.Focused ? Color.FromArgb(2, 132, 199) : Color.FromArgb(203, 213, 225),
                1.2f))
            {
                g.FillPath(ib, ip);
                g.DrawPath(iPen, ip);
            }

            _rectBrowseBtn = new Rectangle(inputBoxRect.Right + S(8), inputY, S(62), S(34));
            DrawSecondaryButton(g, _rectBrowseBtn, "浏览...", _hoveredId == "browse");

            // 3 Checkboxes Row
            int chkY = S(444);
            _rectChkDesktop = DrawCustomCheckbox(g, contentX + S(2), chkY, "桌面快捷方式", _optDesktopShortcut, _hoveredId == "chk_desktop");
            _rectChkStartMenu = DrawCustomCheckbox(g, _rectChkDesktop.Right + S(18), chkY, "开始菜单图标", _optStartMenuShortcut, _hoveredId == "chk_start");
            _rectChkTeproj = DrawCustomCheckbox(g, _rectChkStartMenu.Right + S(18), chkY, "关联 .teproj 工程", _optAssociateTeproj, _hoveredId == "chk_teproj");
        }

        private void DrawUninstallReadyBody(Graphics g, int w, int headerH)
        {
            int contentX = S(40);
            int contentW = w - S(80);

            Rectangle cardRect = new Rectangle(contentX, headerH + S(28), contentW, S(74));
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(12)))
            using (SolidBrush cf = new SolidBrush(Color.FromArgb(254, 242, 242)))
            using (Pen cPen = new Pen(Color.FromArgb(254, 202, 202), 1f))
            using (Font f = new Font("Microsoft YaHei UI", 9.2f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(Color.FromArgb(127, 29, 29)))
            {
                g.FillPath(cf, cp);
                g.DrawPath(cPen, cp);
                RectangleF tr = new RectangleF(cardRect.X + S(14), cardRect.Y + S(10), cardRect.Width - S(28), cardRect.Height - S(16));
                StringFormat sf = new StringFormat();
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(
                    "即将移除 PhonAcq 程序文件、桌面快捷方式与系统关联。\n您保存在本地语料库中的历史录音 (.wav) 与调查字表数据不会被删除。",
                    f, tb, tr, sf);
            }

            int btnW = S(176);
            int btnH = S(46);
            int gap = S(16);
            int startX = (w - (btnW * 2 + gap)) / 2;
            int btnY = headerH + S(126);

            _rectSecondaryBtn = new Rectangle(startX, btnY, btnW, btnH);
            _rectPrimaryBtn = new Rectangle(startX + btnW + gap, btnY, btnW, btnH);

            DrawSecondaryButton(g, _rectSecondaryBtn, "取消", _hoveredId == "secondary");
            DrawPrimaryActionButton(g, _rectPrimaryBtn, "确认卸载 PhonAcq", _hoveredId == "primary", true);
        }

        private void DrawWorkingProgressBody(Graphics g, int w, int headerH)
        {
            int contentX = S(48);
            int contentW = w - S(96);

            using (Font pctFont = new Font("Segoe UI", 24f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush pctBrush = new SolidBrush(Color.FromArgb(2, 132, 199)))
            {
                string pctStr = String.Format("{0}%", _progressPercent);
                StringFormat csf = new StringFormat();
                csf.Alignment = StringAlignment.Center;
                g.DrawString(pctStr, pctFont, pctBrush, new RectangleF(0, headerH + S(38), w, S(44)), csf);
            }

            // Progress Track
            Rectangle trackRect = new Rectangle(contentX, headerH + S(94), contentW, S(14));
            using (GraphicsPath tp = CreateRoundRect(trackRect, S(7)))
            using (SolidBrush tb = new SolidBrush(Color.FromArgb(226, 232, 240)))
            {
                g.FillPath(tb, tp);
            }

            int fillW = Math.Max(S(14), (int)((contentW * _progressPercent) / 100.0));
            if (_progressPercent > 0)
            {
                Rectangle fillRect = new Rectangle(contentX, headerH + S(94), Math.Min(contentW, fillW), S(14));
                using (GraphicsPath fp = CreateRoundRect(fillRect, S(7)))
                using (LinearGradientBrush fb = new LinearGradientBrush(
                    trackRect,
                    Color.FromArgb(2, 132, 199),
                    Color.FromArgb(6, 182, 212),
                    0f))
                {
                    g.FillPath(fb, fp);
                }
            }

            using (Font stFont = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush stBrush = new SolidBrush(Color.FromArgb(71, 85, 105)))
            {
                StringFormat csf = new StringFormat();
                csf.Alignment = StringAlignment.Center;
                g.DrawString(_statusMessage, stFont, stBrush, new RectangleF(S(30), headerH + S(124), w - S(60), S(28)), csf);
            }
        }

        private void DrawCompletedBody(Graphics g, int w, int headerH)
        {
            // Emerald Success Circle
            int cSize = S(52);
            Rectangle circleRect = new Rectangle((w - cSize) / 2, headerH + S(24), cSize, cSize);
            using (SolidBrush fill = new SolidBrush(Color.FromArgb(220, 252, 231)))
            using (Pen ring = new Pen(Color.FromArgb(22, 163, 74), Math.Max(2f, 2.2f * _dpiScale)))
            {
                g.FillEllipse(fill, circleRect);
                g.DrawEllipse(ring, circleRect);
                ring.StartCap = LineCap.Round;
                ring.EndCap = LineCap.Round;
                ring.LineJoin = LineJoin.Round;
                Point p1 = new Point(circleRect.X + S(15), circleRect.Y + S(27));
                Point p2 = new Point(circleRect.X + S(23), circleRect.Y + S(35));
                Point p3 = new Point(circleRect.X + S(38), circleRect.Y + S(18));
                g.DrawLines(ring, new Point[] { p1, p2, p3 });
            }

            using (Font hFont = new Font("Microsoft YaHei UI", 13.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush hb = new SolidBrush(Color.FromArgb(15, 23, 42)))
            {
                StringFormat csf = new StringFormat();
                csf.Alignment = StringAlignment.Center;
                string title = _isUninstallMode ? "PhonAcq 已卸载完成" : String.Format("PhonAcq v{0} 安装成功", _appVersion);
                g.DrawString(title, hFont, hb, new RectangleF(0, headerH + S(86), w, S(30)), csf);
            }

            if (_isUninstallMode)
            {
                int btnW = S(180);
                _rectPrimaryBtn = new Rectangle((w - btnW) / 2, headerH + S(136), btnW, S(46));
                DrawPrimaryActionButton(g, _rectPrimaryBtn, "完成并退出", _hoveredId == "primary", false);
            }
            else
            {
                int btnW1 = S(210);
                int btnW2 = S(116);
                int gap = S(14);
                int startX = (w - (btnW1 + btnW2 + gap)) / 2;
                int btnY = headerH + S(136);

                _rectPrimaryBtn = new Rectangle(startX, btnY, btnW1, S(46));
                _rectSecondaryBtn = new Rectangle(startX + btnW1 + gap, btnY, btnW2, S(46));

                DrawPrimaryActionButton(g, _rectPrimaryBtn, "立即打开 PhonAcq", _hoveredId == "primary", false);
                DrawSecondaryButton(g, _rectSecondaryBtn, "完成", _hoveredId == "secondary");
            }
        }

        private void DrawErrorBody(Graphics g, int w, int headerH)
        {
            Rectangle errRect = new Rectangle(S(36), headerH + S(26), w - S(72), S(88));
            using (GraphicsPath ep = CreateRoundRect(errRect, S(12)))
            using (SolidBrush ef = new SolidBrush(Color.FromArgb(254, 242, 242)))
            using (Pen ePen = new Pen(Color.FromArgb(248, 113, 113), 1f))
            using (Font f = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(Color.FromArgb(153, 27, 27)))
            {
                g.FillPath(ef, ep);
                g.DrawPath(ePen, ep);
                RectangleF tr = new RectangleF(errRect.X + S(14), errRect.Y + S(10), errRect.Width - S(28), errRect.Height - S(20));
                g.DrawString("安装遇到问题：" + _errorMessage, f, tb, tr);
            }

            int btnW = S(160);
            int gap = S(14);
            int startX = (w - (btnW * 2 + gap)) / 2;
            _rectPrimaryBtn = new Rectangle(startX, headerH + S(136), btnW, S(44));
            _rectSecondaryBtn = new Rectangle(startX + btnW + gap, headerH + S(136), btnW, S(44));

            DrawPrimaryActionButton(g, _rectPrimaryBtn, "重试", _hoveredId == "primary", false);
            DrawSecondaryButton(g, _rectSecondaryBtn, "退出", _hoveredId == "secondary");
        }

        private void DrawPrimaryActionButton(Graphics g, Rectangle r, string text, bool hovered, bool danger)
        {
            Color c1 = danger
                ? (hovered ? Color.FromArgb(185, 28, 28) : Color.FromArgb(220, 38, 38))
                : (hovered ? Color.FromArgb(3, 105, 161) : Color.FromArgb(2, 132, 199));
            Color c2 = danger
                ? (hovered ? Color.FromArgb(220, 38, 38) : Color.FromArgb(239, 68, 68))
                : (hovered ? Color.FromArgb(2, 132, 199) : Color.FromArgb(14, 165, 233));

            using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
            using (LinearGradientBrush b = new LinearGradientBrush(r, c1, c2, 0f))
            using (Font f = new Font("Microsoft YaHei UI", 11.2f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(Color.White))
            {
                g.FillPath(b, p);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawSecondaryButton(Graphics g, Rectangle r, string text, bool hovered)
        {
            Color fill = hovered ? Color.FromArgb(224, 242, 254) : Color.White;
            Color border = hovered ? Color.FromArgb(56, 189, 248) : Color.FromArgb(203, 213, 225);
            Color fg = hovered ? Color.FromArgb(3, 105, 161) : Color.FromArgb(51, 65, 85);

            using (GraphicsPath p = CreateRoundRect(r, S(9)))
            using (SolidBrush b = new SolidBrush(fill))
            using (Pen pen = new Pen(border, 1f))
            using (Font f = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(fg))
            {
                g.FillPath(b, p);
                g.DrawPath(pen, p);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private Rectangle DrawCustomCheckbox(Graphics g, int x, int y, string label, bool isChecked, bool hovered)
        {
            int boxSz = S(18);
            Rectangle box = new Rectangle(x, y, boxSz, boxSz);
            Color fill = isChecked
                ? Color.FromArgb(2, 132, 199)
                : (hovered ? Color.FromArgb(224, 242, 254) : Color.White);
            Color border = (isChecked || hovered)
                ? Color.FromArgb(2, 132, 199)
                : Color.FromArgb(148, 163, 184);

            using (GraphicsPath bp = CreateRoundRect(box, S(5)))
            using (SolidBrush bf = new SolidBrush(fill))
            using (Pen bPen = new Pen(border, 1.4f))
            {
                g.FillPath(bf, bp);
                g.DrawPath(bPen, bp);
            }

            if (isChecked)
            {
                using (Pen ckPen = new Pen(Color.White, Math.Max(1.6f, 1.8f * _dpiScale)))
                {
                    ckPen.StartCap = LineCap.Round;
                    ckPen.EndCap = LineCap.Round;
                    ckPen.LineJoin = LineJoin.Round;
                    g.DrawLines(ckPen, new Point[]
                    {
                        new Point(box.X + S(4), box.Y + S(9)),
                        new Point(box.X + S(8), box.Y + S(13)),
                        new Point(box.X + S(14), box.Y + S(5))
                    });
                }
            }

            using (Font f = new Font("Microsoft YaHei UI", 8.8f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(Color.FromArgb(51, 65, 85)))
            {
                SizeF sz = g.MeasureString(label, f);
                g.DrawString(label, f, tb, box.Right + S(6), y + (boxSz - sz.Height) / 2f);
                return new Rectangle(x, y - S(3), boxSz + S(8) + (int)sz.Width, boxSz + S(6));
            }
        }

        private string HitTestId(Point pt)
        {
            if (_rectCloseBtn.Contains(pt) && _stage != SetupStage.Working) return "close";
            if (_rectMinBtn.Contains(pt)) return "min";
            if (_rectPrimaryBtn.Contains(pt)) return "primary";
            if (_rectSecondaryBtn.Contains(pt)) return "secondary";
            if (_rectExpandToggle.Contains(pt)) return "toggle";
            if (_rectBrowseBtn.Contains(pt)) return "browse";
            if (_rectChkDesktop.Contains(pt)) return "chk_desktop";
            if (_rectChkStartMenu.Contains(pt)) return "chk_start";
            if (_rectChkTeproj.Contains(pt)) return "chk_teproj";
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            string hit = HitTestId(e.Location);
            if (hit != _hoveredId)
            {
                _hoveredId = hit;
                Cursor = hit != null ? Cursors.Hand : Cursors.Default;
                Invalidate();
            }
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            if (_hoveredId != null)
            {
                _hoveredId = null;
                Cursor = Cursors.Default;
                Invalidate();
            }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left) return;

            string hit = HitTestId(e.Location);
            if (hit == null && e.Y <= S(148))
            {
                // Drag borderless studio window smoothly by its header
                NativeMethods.ReleaseCapture();
                NativeMethods.SendMessage(Handle, NativeMethods.WM_NCLBUTTONDOWN, NativeMethods.HTCAPTION, 0);
                return;
            }

            if (hit == "min")
            {
                WindowState = FormWindowState.Minimized;
            }
            else if (hit == "close")
            {
                if (_stage != SetupStage.Working) Close();
            }
            else if (hit == "toggle")
            {
                _optionsExpanded = !_optionsExpanded;
                ApplyWindowGeometry();
            }
            else if (hit == "browse")
            {
                string picked = ModernFolderBrowser.PickFolder(Handle, _installDir, "选择 PhonAcq 安装目录");
                if (!string.IsNullOrEmpty(picked))
                {
                    if (!picked.EndsWith("PhonAcq", StringComparison.OrdinalIgnoreCase))
                    {
                        picked = Path.Combine(picked, "PhonAcq");
                    }
                    _installDir = picked;
                    _pathTextBox.Text = _installDir;
                    Invalidate();
                }
            }
            else if (hit == "chk_desktop")
            {
                _optDesktopShortcut = !_optDesktopShortcut;
                Invalidate();
            }
            else if (hit == "chk_start")
            {
                _optStartMenuShortcut = !_optStartMenuShortcut;
                Invalidate();
            }
            else if (hit == "chk_teproj")
            {
                _optAssociateTeproj = !_optAssociateTeproj;
                Invalidate();
            }
            else if (hit == "primary")
            {
                HandlePrimaryAction();
            }
            else if (hit == "secondary")
            {
                Close();
            }
        }

        private void HandlePrimaryAction()
        {
            if (_stage == SetupStage.Completed)
            {
                if (!_isUninstallMode)
                {
                    LaunchInstalledApp();
                }
                Close();
                return;
            }

            if (_stage != SetupStage.Ready && _stage != SetupStage.Error) return;

            if (!_isUninstallMode && _payload == null)
            {
                _stage = SetupStage.Error;
                _errorMessage = "未找到安装包内嵌程序数据，请重新下载完整的 PhonAcq 安装程序。";
                ApplyWindowGeometry();
                return;
            }

            _stage = SetupStage.Working;
            _progressPercent = 2;
            _statusMessage = _isUninstallMode ? "正在准备卸载 PhonAcq..." : "正在准备安装环境...";
            ApplyWindowGeometry();

            string targetDir = _installDir;
            bool mkDesktop = _optDesktopShortcut;
            bool mkStart = _optStartMenuShortcut;
            bool assocTeproj = _optAssociateTeproj;
            string selfExe = Application.ExecutablePath;

            Thread worker = new Thread(delegate()
            {
                try
                {
                    if (_isUninstallMode)
                    {
                        InstallerEngine.PerformUninstall(targetDir, selfExe, UpdateProgressSafe);
                    }
                    else
                    {
                        InstallerEngine.PerformInstall(
                            selfExe,
                            _payload,
                            targetDir,
                            mkDesktop,
                            mkStart,
                            assocTeproj,
                            _legacyMsiCodes,
                            UpdateProgressSafe);
                    }

                    if (!IsDisposed)
                    {
                        BeginInvoke((MethodInvoker)delegate
                        {
                            _stage = SetupStage.Completed;
                            Invalidate();
                        });
                    }
                }
                catch (Exception ex)
                {
                    if (!IsDisposed)
                    {
                        BeginInvoke((MethodInvoker)delegate
                        {
                            _stage = SetupStage.Error;
                            _errorMessage = ex.Message;
                            Invalidate();
                        });
                    }
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }

        private void UpdateProgressSafe(int pct, string msg)
        {
            if (IsDisposed || !IsHandleCreated) return;
            try
            {
                BeginInvoke((MethodInvoker)delegate
                {
                    _progressPercent = Math.Max(0, Math.Min(100, pct));
                    _statusMessage = msg ?? "";
                    Invalidate();
                });
            }
            catch
            {
            }
        }

        private void LaunchInstalledApp()
        {
            try
            {
                string mainExe = Path.Combine(_installDir, "PhonAcq.exe");
                if (File.Exists(mainExe))
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = mainExe;
                    psi.WorkingDirectory = _installDir;
                    psi.UseShellExecute = true;
                    Process.Start(psi);
                }
            }
            catch
            {
            }
        }
    }

    internal static class Program
    {
        // Replaced at build time by Gradle packageWindowsSetup if -PappVersion is provided
        public const string BUILD_APP_VERSION = "__PHONACQ_VERSION__";

        [STAThread]
        public static int Main(string[] args)
        {
            NativeMethods.EnableHighDpi();
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            bool isUninstall = false;
            bool isSilent = false;
            string customDir = null;

            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    string a = args[i] ?? "";
                    if (string.Equals(a, "--uninstall", StringComparison.OrdinalIgnoreCase) ||
                        string.Equals(a, "/uninstall", StringComparison.OrdinalIgnoreCase))
                    {
                        isUninstall = true;
                    }
                    else if (string.Equals(a, "--silent", StringComparison.OrdinalIgnoreCase) ||
                             string.Equals(a, "/S", StringComparison.OrdinalIgnoreCase) ||
                             string.Equals(a, "/quiet", StringComparison.OrdinalIgnoreCase))
                    {
                        isSilent = true;
                    }
                    else if (a.StartsWith("--dir=", StringComparison.OrdinalIgnoreCase))
                    {
                        customDir = a.Substring(6).Trim('"');
                    }
                }
            }

            string ver = BUILD_APP_VERSION.StartsWith("__") ? "2.0.2" : BUILD_APP_VERSION;
            string selfExe = Application.ExecutablePath;
            PayloadDescriptor payload = isUninstall ? null : InstallerEngine.ReadPayloadDescriptor(selfExe, ver);

            if (isSilent)
            {
                try
                {
                    string detectedDir;
                    string detectedVer;
                    List<string> msiCodes;
                    InstallerEngine.DetectExistingInstallation(out detectedDir, out detectedVer, out msiCodes);

                    if (isUninstall)
                    {
                        string target = !string.IsNullOrEmpty(detectedDir) ? detectedDir : Path.GetDirectoryName(selfExe);
                        InstallerEngine.PerformUninstall(target, selfExe, null);
                        return 0;
                    }
                    else if (payload != null)
                    {
                        string target = !string.IsNullOrEmpty(customDir)
                            ? customDir
                            : InstallerEngine.GetDefaultInstallDir(detectedDir);
                        InstallerEngine.PerformInstall(selfExe, payload, target, true, true, true, msiCodes, null);
                        return 0;
                    }
                    return 2;
                }
                catch
                {
                    return 1;
                }
            }

            Application.Run(new PhonAcqInstallerForm(isUninstall, ver, payload));
            return 0;
        }
    }
}
