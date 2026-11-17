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
        public const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
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

        public static bool IsSystemDarkTheme()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"))
                {
                    if (key != null)
                    {
                        object val = key.GetValue("AppsUseLightTheme");
                        if (val is int)
                        {
                            return ((int)val) == 0;
                        }
                    }
                }
            }
            catch
            {
            }
            return false;
        }
    }

    /// <summary>
    /// Modern Windows 10/11 COM IFileOpenDialog (FOS_PICKFOLDERS) for selecting the installation folder.
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
                dialog.SetOkButtonLabel("选择文件夹");

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
    /// Exact Material You (Material 3) solid tonal color tokens matching PhonAcq's Theme.kt / Color.kt.
    /// Strictly flat tonal hierarchy with zero gradients.
    /// </summary>
    internal sealed class MaterialYouPalette
    {
        public readonly bool IsDark;
        public readonly Color Surface;
        public readonly Color SurfaceContainer;
        public readonly Color SurfaceContainerHigh;
        public readonly Color InputSurface;
        public readonly Color Primary;
        public readonly Color PrimaryHover;
        public readonly Color OnPrimary;
        public readonly Color PrimaryContainer;
        public readonly Color PrimaryContainerHover;
        public readonly Color OnPrimaryContainer;
        public readonly Color OnSurface;
        public readonly Color OnSurfaceVariant;
        public readonly Color Outline;
        public readonly Color OutlineVariant;
        public readonly Color WindowBorder;
        public readonly Color Error;
        public readonly Color ErrorHover;
        public readonly Color OnError;
        public readonly Color ErrorContainer;
        public readonly Color OnErrorContainer;

        public MaterialYouPalette(bool dark)
        {
            IsDark = dark;
            if (dark)
            {
                Surface = Color.FromArgb(30, 41, 59);                // #1E293B SurfaceDark
                SurfaceContainer = Color.FromArgb(36, 50, 71);       // #243247 SurfaceContainerDark
                SurfaceContainerHigh = Color.FromArgb(51, 65, 85);   // #334155 SurfaceContainerHighestDark
                InputSurface = Color.FromArgb(15, 23, 42);           // #0F172A BGDark
                Primary = Color.FromArgb(96, 165, 250);              // #60A5FA PrimaryBlueDark
                PrimaryHover = Color.FromArgb(147, 197, 253);        // #93C5FD
                OnPrimary = Color.FromArgb(15, 23, 42);              // #0F172A
                PrimaryContainer = Color.FromArgb(30, 58, 138);      // #1E3A8A PrimaryContainerDark
                PrimaryContainerHover = Color.FromArgb(37, 99, 235); // #2563EB
                OnPrimaryContainer = Color.FromArgb(219, 234, 254);  // #DBEAFE OnPrimaryContainerDark
                OnSurface = Color.FromArgb(248, 250, 252);           // #F8FAFC TextLight
                OnSurfaceVariant = Color.FromArgb(148, 163, 184);    // #94A3B8 TextMutedDark
                Outline = Color.FromArgb(148, 163, 184);             // #94A3B8 BorderDark
                OutlineVariant = Color.FromArgb(51, 65, 85);         // #334155
                WindowBorder = Color.FromArgb(71, 85, 105);          // #475569 BorderVariantDark
                Error = Color.FromArgb(248, 113, 113);               // #F87171
                ErrorHover = Color.FromArgb(252, 165, 165);          // #FCA5A5
                OnError = Color.FromArgb(69, 10, 10);                // #450A0A
                ErrorContainer = Color.FromArgb(69, 26, 26);         // #451A1A
                OnErrorContainer = Color.FromArgb(254, 202, 202);    // #FECACA
            }
            else
            {
                Surface = Color.FromArgb(255, 255, 255);             // #FFFFFF SurfaceNeutral
                SurfaceContainer = Color.FromArgb(241, 245, 249);    // #F1F5F9 SurfaceContainerLight
                SurfaceContainerHigh = Color.FromArgb(226, 232, 240);// #E2E8F0 SurfaceContainerHighLight
                InputSurface = Color.FromArgb(255, 255, 255);        // #FFFFFF SurfaceContainerLowest
                Primary = Color.FromArgb(29, 78, 216);               // #1D4ED8 PrimaryBlue
                PrimaryHover = Color.FromArgb(30, 64, 175);          // #1E40AF
                OnPrimary = Color.FromArgb(255, 255, 255);           // #FFFFFF
                PrimaryContainer = Color.FromArgb(219, 234, 254);    // #DBEAFE PrimaryContainerBlue
                PrimaryContainerHover = Color.FromArgb(191, 219, 254);// #BFDBFE
                OnPrimaryContainer = Color.FromArgb(30, 58, 138);    // #1E3A8A OnPrimaryContainerBlue
                OnSurface = Color.FromArgb(15, 23, 42);              // #0F172A TextDark
                OnSurfaceVariant = Color.FromArgb(71, 85, 105);      // #475569 TextMuted
                Outline = Color.FromArgb(100, 116, 139);             // #64748B BorderMuted
                OutlineVariant = Color.FromArgb(203, 213, 225);      // #CBD5E1 BorderLight
                WindowBorder = Color.FromArgb(203, 213, 225);        // #CBD5E1
                Error = Color.FromArgb(220, 38, 38);                 // #DC2626
                ErrorHover = Color.FromArgb(185, 28, 28);            // #B91C1C
                OnError = Color.FromArgb(255, 255, 255);             // #FFFFFF
                ErrorContainer = Color.FromArgb(254, 226, 226);      // #FEE2E2
                OnErrorContainer = Color.FromArgb(127, 29, 29);      // #7F1D1D
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

        /// <summary>
        /// PhonAcq intentionally does NOT associate .teproj (which belongs to PhonTracer).
        /// If an earlier PhonAcq build wrote a PhonAcq.teproj key, clean only that entry without touching PhonTracer.
        /// </summary>
        public static void CleanupLegacyPhonAcqTeprojAssociation()
        {
            try
            {
                bool changed = false;
                using (RegistryKey progKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\PhonAcq.teproj"))
                {
                    if (progKey != null) changed = true;
                }
                if (changed)
                {
                    Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\PhonAcq.teproj", false);
                }

                bool removeDotKey = false;
                using (RegistryKey extKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\.teproj", false))
                {
                    if (extKey != null)
                    {
                        string cur = extKey.GetValue("") as string;
                        if (string.Equals(cur, "PhonAcq.teproj", StringComparison.OrdinalIgnoreCase))
                        {
                            removeDotKey = true;
                        }
                    }
                }
                if (removeDotKey)
                {
                    Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\.teproj", false);
                    changed = true;
                }

                if (changed)
                {
                    NativeMethods.SHChangeNotify(NativeMethods.SHCNE_ASSOCCHANGED, NativeMethods.SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
                }
            }
            catch
            {
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
            List<string> legacyMsiCodes,
            Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(4, "正在关闭运行中的 PhonAcq...");
            StopRunningPhonAcqProcesses();

            if (legacyMsiCodes != null && legacyMsiCodes.Count > 0)
            {
                if (onProgress != null) onProgress(9, "正在更新旧版安装记录...");
                CleanupLegacyPerUserMsi(legacyMsiCodes);
            }

            if (!Directory.Exists(installDir))
            {
                Directory.CreateDirectory(installDir);
            }

            string oldAppSubdir = Path.Combine(installDir, "app");
            string oldRuntimeSubdir = Path.Combine(installDir, "runtime");
            string oldLibSubdir = Path.Combine(installDir, "lib");
            try
            {
                if (Directory.Exists(oldAppSubdir)) Directory.Delete(oldAppSubdir, true);
                if (Directory.Exists(oldRuntimeSubdir)) Directory.Delete(oldRuntimeSubdir, true);
                if (Directory.Exists(oldLibSubdir)) Directory.Delete(oldLibSubdir, true);
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
                            onProgress(pct, "正在复制程序文件...");
                        }
                    }
                }
            }

            if (onProgress != null) onProgress(93, "正在注册应用信息...");

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
            CleanupLegacyPhonAcqTeprojAssociation();

            if (onProgress != null) onProgress(97, "正在创建快捷方式...");

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

            if (onProgress != null) onProgress(100, "安装完成");
        }

        public static void PerformUninstall(string installDir, string selfExePath, Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(15, "正在关闭运行中的 PhonAcq...");
            StopRunningPhonAcqProcesses();

            if (onProgress != null) onProgress(45, "正在移除快捷方式...");
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

            if (onProgress != null) onProgress(70, "正在清理应用注册信息...");
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(UNINSTALL_REG_SUBKEY, false);
            }
            catch
            {
            }
            CleanupLegacyPhonAcqTeprojAssociation();

            if (onProgress != null) onProgress(88, "正在移除程序文件...");
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

            if (onProgress != null) onProgress(100, "卸载完成");
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
        private readonly MaterialYouPalette _theme;

        private string _installDir;
        private readonly string _existingVersion;
        private readonly bool _isOverwriteUpgrade;
        private readonly List<string> _legacyMsiCodes;

        private SetupStage _stage = SetupStage.Ready;
        private bool _optDesktopShortcut = true;
        private bool _optStartMenuShortcut = true;

        private int _progressPercent = 0;
        private string _statusMessage = "";
        private string _errorMessage = "";

        private float _dpiScale = 1.0f;
        private readonly TextBox _pathTextBox;

        private Rectangle _rectMinBtn;
        private Rectangle _rectCloseBtn;
        private Rectangle _rectPrimaryBtn;
        private Rectangle _rectSecondaryBtn;
        private Rectangle _rectBrowseBtn;
        private Rectangle _rectChkDesktop;
        private Rectangle _rectChkStartMenu;

        private string _hoveredId = null;

        public PhonAcqInstallerForm(bool isUninstallMode, string appVersion, PayloadDescriptor payload)
        {
            _isUninstallMode = isUninstallMode;
            _appVersion = appVersion;
            _payload = payload;
            _theme = new MaterialYouPalette(NativeMethods.IsSystemDarkTheme());

            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.UserPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.ResizeRedraw,
                true);

            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = _theme.Surface;
            Text = isUninstallMode ? "卸载 PhonAcq" : "安装 PhonAcq";

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
            _pathTextBox.BackColor = _theme.InputSurface;
            _pathTextBox.ForeColor = _theme.OnSurface;
            _pathTextBox.Text = _installDir;
            _pathTextBox.Visible = false;
            _pathTextBox.TextChanged += delegate { _installDir = _pathTextBox.Text.Trim(); };
            _pathTextBox.GotFocus += delegate { Invalidate(); };
            _pathTextBox.LostFocus += delegate { Invalidate(); };
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
            int w = S(508);
            int h = S(376);
            ClientSize = new Size(w, h);

            if (_theme.IsDark)
            {
                try
                {
                    int darkPref = 1;
                    NativeMethods.DwmSetWindowAttribute(
                        Handle,
                        NativeMethods.DWMWA_USE_IMMERSIVE_DARK_MODE,
                        ref darkPref,
                        sizeof(int));
                }
                catch
                {
                }
            }

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
                IntPtr rgn = NativeMethods.CreateRoundRectRgn(0, 0, w + 1, h + 1, S(24), S(24));
                Region = Region.FromHrgn(rgn);
            }

            LayoutPathTextBox();
            Invalidate();
        }

        private void LayoutPathTextBox()
        {
            bool showPath = !_isUninstallMode && _stage == SetupStage.Ready;
            _pathTextBox.Visible = showPath;
            if (showPath)
            {
                _pathTextBox.Font = new Font("Microsoft YaHei UI", 9.3f, FontStyle.Regular, GraphicsUnit.Point);
                int boxX = S(46);
                int boxY = S(148);
                int boxW = S(330);
                int boxH = S(38);
                _pathTextBox.Location = new Point(boxX + S(12), boxY + (boxH - _pathTextBox.PreferredHeight) / 2);
                _pathTextBox.Width = boxW - S(24);
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

            // Pure flat Material You surface canvas (zero gradients)
            using (SolidBrush bgBrush = new SolidBrush(_theme.Surface))
            {
                g.FillRectangle(bgBrush, 0, 0, w, h);
            }

            DrawWindowControls(g, w);
            DrawBrandHeader(g);

            _rectPrimaryBtn = Rectangle.Empty;
            _rectSecondaryBtn = Rectangle.Empty;
            _rectBrowseBtn = Rectangle.Empty;
            _rectChkDesktop = Rectangle.Empty;
            _rectChkStartMenu = Rectangle.Empty;

            if (_stage == SetupStage.Ready)
            {
                if (_isUninstallMode)
                {
                    DrawUninstallReadyBody(g, w);
                }
                else
                {
                    DrawInstallReadyBody(g, w);
                }
            }
            else if (_stage == SetupStage.Working)
            {
                DrawWorkingProgressBody(g, w);
            }
            else if (_stage == SetupStage.Completed)
            {
                DrawCompletedBody(g, w);
            }
            else if (_stage == SetupStage.Error)
            {
                DrawErrorBody(g, w);
            }

            // Subtle hairline border for Windows 10 fallback clarity
            using (Pen borderPen = new Pen(_theme.WindowBorder, 1f))
            {
                g.DrawRectangle(borderPen, 0, 0, w - 1, h - 1);
            }
        }

        private void DrawWindowControls(Graphics g, int w)
        {
            int btnSz = S(32);
            _rectCloseBtn = new Rectangle(w - S(44), S(12), btnSz, btnSz);
            _rectMinBtn = new Rectangle(w - S(80), S(12), btnSz, btnSz);

            bool closeEnabled = _stage != SetupStage.Working;
            if (_hoveredId == "close" && closeEnabled)
            {
                using (SolidBrush b = new SolidBrush(_theme.ErrorContainer))
                {
                    g.FillEllipse(b, _rectCloseBtn);
                }
            }
            if (_hoveredId == "min")
            {
                using (SolidBrush b = new SolidBrush(_theme.SurfaceContainerHigh))
                {
                    g.FillEllipse(b, _rectMinBtn);
                }
            }

            float strokeW = Math.Max(1.4f, 1.5f * _dpiScale);
            using (Pen minPen = new Pen(_theme.OnSurfaceVariant, strokeW))
            {
                minPen.StartCap = LineCap.Round;
                minPen.EndCap = LineCap.Round;
                int my = _rectMinBtn.Y + _rectMinBtn.Height / 2;
                g.DrawLine(minPen, _rectMinBtn.X + S(10), my, _rectMinBtn.Right - S(10), my);
            }

            Color closeGlyphCol = !closeEnabled
                ? _theme.OutlineVariant
                : (_hoveredId == "close" ? _theme.Error : _theme.OnSurfaceVariant);
            using (Pen closePen = new Pen(closeGlyphCol, strokeW))
            {
                closePen.StartCap = LineCap.Round;
                closePen.EndCap = LineCap.Round;
                int cx1 = _rectCloseBtn.X + S(11);
                int cy1 = _rectCloseBtn.Y + S(11);
                int cx2 = _rectCloseBtn.Right - S(11);
                int cy2 = _rectCloseBtn.Bottom - S(11);
                g.DrawLine(closePen, cx1, cy1, cx2, cy2);
                g.DrawLine(closePen, cx2, cy1, cx1, cy2);
            }
        }

        private void DrawBrandHeader(Graphics g)
        {
            int logoSize = S(52);
            int logoX = S(28);
            int logoY = S(30);
            if (_logoImage != null)
            {
                g.DrawImage(_logoImage, new Rectangle(logoX, logoY, logoSize, logoSize));
            }
            else
            {
                using (GraphicsPath p = CreateRoundRect(new Rectangle(logoX, logoY, logoSize, logoSize), S(16)))
                using (SolidBrush b = new SolidBrush(_theme.PrimaryContainer))
                {
                    g.FillPath(b, p);
                }
            }

            int textLeft = logoX + logoSize + S(16);
            using (Font titleFont = new Font("Segoe UI", 18.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush titleBrush = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString("PhonAcq", titleFont, titleBrush, textLeft - S(2), logoY - S(1));
                SizeF titleSz = g.MeasureString("PhonAcq", titleFont);

                // Flat M3 PrimaryContainer stadium chip (no border, no geeky arch string)
                string chipText = _isUninstallMode ? "卸载程序" : ("v" + _appVersion);
                using (Font chipFont = new Font("Segoe UI Semibold", 8.8f, FontStyle.Bold, GraphicsUnit.Point))
                using (SolidBrush chipFill = new SolidBrush(_theme.PrimaryContainer))
                using (SolidBrush chipFg = new SolidBrush(_theme.OnPrimaryContainer))
                {
                    SizeF cSz = g.MeasureString(chipText, chipFont);
                    int chipH = S(22);
                    int chipW = (int)cSz.Width + S(14);
                    Rectangle chipRect = new Rectangle(
                        textLeft + (int)titleSz.Width + S(4),
                        logoY + S(7),
                        chipW,
                        chipH);
                    using (GraphicsPath cp = CreateRoundRect(chipRect, chipH / 2))
                    {
                        g.FillPath(chipFill, cp);
                    }
                    StringFormat csf = new StringFormat();
                    csf.Alignment = StringAlignment.Center;
                    csf.LineAlignment = StringAlignment.Center;
                    g.DrawString(chipText, chipFont, chipFg, chipRect, csf);
                }
            }

            using (Font subFont = new Font("Microsoft YaHei UI", 9.3f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush subBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                string subtitle = _isUninstallMode
                    ? "从本机移除 PhonAcq 桌面端程序"
                    : "田野语音与方言字表采录系统";
                g.DrawString(subtitle, subFont, subBrush, textLeft, logoY + S(33));
            }
        }

        private void DrawInstallReadyBody(Graphics g, int w)
        {
            int cardX = S(28);
            int cardW = w - S(56);

            // Flat Material You SurfaceContainer card (20dp corner radius, zero border)
            Rectangle cardRect = new Rectangle(cardX, S(104), cardW, S(172));
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(20)))
            using (SolidBrush cb = new SolidBrush(_theme.SurfaceContainer))
            {
                g.FillPath(cb, cp);
            }

            // 1. Install Location Field + Flat Tonal Button
            using (Font lblFont = new Font("Microsoft YaHei UI", 8.8f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush lblBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                g.DrawString("安装位置", lblFont, lblBrush, S(46), S(122));
            }

            Rectangle inputRect = new Rectangle(S(46), S(148), S(330), S(38));
            Color inputBorder = _pathTextBox.Focused ? _theme.Primary : _theme.OutlineVariant;
            float inputBorderW = _pathTextBox.Focused ? 1.6f : 1.0f;
            using (GraphicsPath ip = CreateRoundRect(inputRect, S(12)))
            using (SolidBrush ib = new SolidBrush(_theme.InputSurface))
            using (Pen iPen = new Pen(inputBorder, inputBorderW))
            {
                g.FillPath(ib, ip);
                g.DrawPath(iPen, ip);
            }

            _rectBrowseBtn = new Rectangle(inputRect.Right + S(10), S(148), S(76), S(38));
            DrawTonalButton(g, _rectBrowseBtn, "更改...", S(12), _hoveredId == "browse");

            // Subtle tonal divider inside card
            using (Pen divPen = new Pen(_theme.OutlineVariant, 1f))
            {
                g.DrawLine(divPen, S(46), S(204), cardRect.Right - S(18), S(204));
            }

            // 2. Shortcuts Row (No .teproj association — PhonTracer owns .teproj)
            int chkY = S(224);
            _rectChkDesktop = DrawMaterialCheckbox(g, S(46), chkY, "创建桌面快捷方式", _optDesktopShortcut, _hoveredId == "chk_desktop");
            _rectChkStartMenu = DrawMaterialCheckbox(g, S(236), chkY, "添加到开始菜单", _optStartMenuShortcut, _hoveredId == "chk_start");

            // 3. Bottom M3 Dialog Footer Row
            int btnY = S(306);
            int btnH = S(42);
            int primaryW = S(118);
            int cancelW = S(76);
            int rightEdge = w - S(28);

            _rectPrimaryBtn = new Rectangle(rightEdge - primaryW, btnY, primaryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - cancelW, btnY, cancelW, btnH);

            if (_isOverwriteUpgrade)
            {
                // Calm, non-intrusive left status indicator for overwrite updates
                int dotSz = S(18);
                Rectangle dotRect = new Rectangle(S(30), btnY + (btnH - dotSz) / 2, dotSz, dotSz);
                using (SolidBrush db = new SolidBrush(_theme.PrimaryContainer))
                using (Pen ckPen = new Pen(_theme.Primary, Math.Max(1.5f, 1.6f * _dpiScale)))
                {
                    g.FillEllipse(db, dotRect);
                    ckPen.StartCap = LineCap.Round;
                    ckPen.EndCap = LineCap.Round;
                    ckPen.LineJoin = LineJoin.Round;
                    g.DrawLines(ckPen, new Point[]
                    {
                        new Point(dotRect.X + S(4), dotRect.Y + S(9)),
                        new Point(dotRect.X + S(8), dotRect.Y + S(13)),
                        new Point(dotRect.X + S(14), dotRect.Y + S(5))
                    });
                }

                using (Font noteFont = new Font("Microsoft YaHei UI", 8.6f, FontStyle.Regular, GraphicsUnit.Point))
                using (SolidBrush noteBrush = new SolidBrush(_theme.OnSurfaceVariant))
                {
                    string verHint = !string.IsNullOrEmpty(_existingVersion)
                        ? String.Format("已安装 v{0} · 覆盖更新保留本地录音与字表", _existingVersion)
                        : "已安装旧版 · 覆盖更新保留本地录音与字表";
                    RectangleF nr = new RectangleF(dotRect.Right + S(7), btnY, _rectSecondaryBtn.X - dotRect.Right - S(14), btnH);
                    StringFormat nsf = new StringFormat();
                    nsf.LineAlignment = StringAlignment.Center;
                    nsf.Trimming = StringTrimming.EllipsisCharacter;
                    nsf.FormatFlags = StringFormatFlags.NoWrap;
                    g.DrawString(verHint, noteFont, noteBrush, nr, nsf);
                }
            }

            DrawTextButton(g, _rectSecondaryBtn, "取消", _hoveredId == "secondary");
            string ctaLabel = _isOverwriteUpgrade ? "覆盖安装" : "开始安装";
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, ctaLabel, _hoveredId == "primary", false);
        }

        private void DrawUninstallReadyBody(Graphics g, int w)
        {
            Rectangle cardRect = new Rectangle(S(28), S(104), w - S(56), S(172));
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(20)))
            using (SolidBrush cb = new SolidBrush(_theme.SurfaceContainer))
            {
                g.FillPath(cb, cp);
            }

            using (Font hFont = new Font("Microsoft YaHei UI", 10.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush hb = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString("确认从本机卸载 PhonAcq？", hFont, hb, S(48), S(128));
            }

            using (Font bFont = new Font("Microsoft YaHei UI", 9.2f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush bb = new SolidBrush(_theme.OnSurfaceVariant))
            {
                RectangleF bodyLayout = new RectangleF(S(48), S(160), cardRect.Width - S(40), S(56));
                g.DrawString(
                    "将移除 PhonAcq 程序文件与桌面快捷方式。\n您在本地保存的所有录音 (.wav) 与调查字表工程将完整保留。",
                    bFont, bb, bodyLayout);
            }

            using (Font pFont = new Font("Microsoft YaHei UI", 8.4f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush pb = new SolidBrush(_theme.Outline))
            {
                StringFormat psf = new StringFormat();
                psf.Trimming = StringTrimming.EllipsisPath;
                psf.FormatFlags = StringFormatFlags.NoWrap;
                RectangleF pr = new RectangleF(S(48), S(232), cardRect.Width - S(40), S(22));
                g.DrawString("安装目录：" + _installDir, pFont, pb, pr, psf);
            }

            int btnY = S(306);
            int btnH = S(42);
            int primaryW = S(104);
            int cancelW = S(76);
            int rightEdge = w - S(28);

            _rectPrimaryBtn = new Rectangle(rightEdge - primaryW, btnY, primaryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - cancelW, btnY, cancelW, btnH);

            DrawTextButton(g, _rectSecondaryBtn, "取消", _hoveredId == "secondary");
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, "卸载", _hoveredId == "primary", true);
        }

        private void DrawWorkingProgressBody(Graphics g, int w)
        {
            Rectangle cardRect = new Rectangle(S(28), S(104), w - S(56), S(172));
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(20)))
            using (SolidBrush cb = new SolidBrush(_theme.SurfaceContainer))
            {
                g.FillPath(cb, cp);
            }

            string workTitle = _isUninstallMode ? "正在卸载 PhonAcq" : "正在安装 PhonAcq";
            using (Font tFont = new Font("Microsoft YaHei UI", 10.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString(workTitle, tFont, tb, S(48), S(138));
            }

            using (Font pctFont = new Font("Segoe UI Semibold", 11f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush pctBrush = new SolidBrush(_theme.Primary))
            {
                StringFormat rsf = new StringFormat();
                rsf.Alignment = StringAlignment.Far;
                g.DrawString(
                    String.Format("{0}%", _progressPercent),
                    pctFont,
                    pctBrush,
                    new RectangleF(S(48), S(137), cardRect.Width - S(40), S(26)),
                    rsf);
            }

            // Pure flat Material 3 LinearProgressIndicator (Primary on PrimaryContainer track)
            int trackX = S(48);
            int trackY = S(178);
            int trackW = cardRect.Width - S(40);
            int trackH = S(8);
            Rectangle trackRect = new Rectangle(trackX, trackY, trackW, trackH);
            using (GraphicsPath tp = CreateRoundRect(trackRect, trackH / 2))
            using (SolidBrush trkBrush = new SolidBrush(_theme.PrimaryContainer))
            {
                g.FillPath(trkBrush, tp);
            }

            if (_progressPercent > 0)
            {
                int fillW = Math.Max(trackH, (int)((trackW * _progressPercent) / 100.0));
                Rectangle fillRect = new Rectangle(trackX, trackY, Math.Min(trackW, fillW), trackH);
                using (GraphicsPath fp = CreateRoundRect(fillRect, trackH / 2))
                using (SolidBrush fillBrush = new SolidBrush(_theme.Primary))
                {
                    g.FillPath(fillBrush, fp);
                }
            }

            using (Font stFont = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush stBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                g.DrawString(_statusMessage, stFont, stBrush, S(48), S(204));
            }
        }

        private void DrawCompletedBody(Graphics g, int w)
        {
            Rectangle cardRect = new Rectangle(S(28), S(104), w - S(56), S(172));
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(20)))
            using (SolidBrush cb = new SolidBrush(_theme.SurfaceContainer))
            {
                g.FillPath(cb, cp);
            }

            // Flat Material You PrimaryContainer icon circle (no ring stroke, no gradient)
            int cSize = S(48);
            Rectangle circleRect = new Rectangle(S(48), S(146), cSize, cSize);
            using (SolidBrush fill = new SolidBrush(_theme.PrimaryContainer))
            using (Pen checkPen = new Pen(_theme.Primary, Math.Max(2.2f, 2.4f * _dpiScale)))
            {
                g.FillEllipse(fill, circleRect);
                checkPen.StartCap = LineCap.Round;
                checkPen.EndCap = LineCap.Round;
                checkPen.LineJoin = LineJoin.Round;
                g.DrawLines(checkPen, new Point[]
                {
                    new Point(circleRect.X + S(14), circleRect.Y + S(25)),
                    new Point(circleRect.X + S(21), circleRect.Y + S(32)),
                    new Point(circleRect.X + S(35), circleRect.Y + S(16))
                });
            }

            int textX = circleRect.Right + S(18);
            using (Font hFont = new Font("Microsoft YaHei UI", 12f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush hb = new SolidBrush(_theme.OnSurface))
            {
                string doneTitle = _isUninstallMode ? "PhonAcq 已卸载" : "安装完成";
                g.DrawString(doneTitle, hFont, hb, textX, S(146));
            }

            using (Font dFont = new Font("Microsoft YaHei UI", 9.2f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush db = new SolidBrush(_theme.OnSurfaceVariant))
            {
                string doneDesc = _isUninstallMode
                    ? "程序文件已移除，本地录音与调查字表数据均已保留。"
                    : "PhonAcq 已准备就绪，现在可以开始语音采录与声学分析。";
                RectangleF dr = new RectangleF(textX, S(176), cardRect.Right - textX - S(20), S(48));
                g.DrawString(doneDesc, dFont, db, dr);
            }

            int btnY = S(306);
            int btnH = S(42);
            int rightEdge = w - S(28);

            if (_isUninstallMode)
            {
                int btnW = S(104);
                _rectPrimaryBtn = new Rectangle(rightEdge - btnW, btnY, btnW, btnH);
                DrawPrimaryFilledButton(g, _rectPrimaryBtn, "完成", _hoveredId == "primary", false);
            }
            else
            {
                int openW = S(136);
                int closeW = S(76);
                _rectPrimaryBtn = new Rectangle(rightEdge - openW, btnY, openW, btnH);
                _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - closeW, btnY, closeW, btnH);

                DrawTextButton(g, _rectSecondaryBtn, "关闭", _hoveredId == "secondary");
                DrawPrimaryFilledButton(g, _rectPrimaryBtn, "打开 PhonAcq", _hoveredId == "primary", false);
            }
        }

        private void DrawErrorBody(Graphics g, int w)
        {
            Rectangle errRect = new Rectangle(S(28), S(104), w - S(56), S(172));
            using (GraphicsPath ep = CreateRoundRect(errRect, S(20)))
            using (SolidBrush ef = new SolidBrush(_theme.ErrorContainer))
            {
                g.FillPath(ef, ep);
            }

            using (Font hFont = new Font("Microsoft YaHei UI", 10.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush hb = new SolidBrush(_theme.OnErrorContainer))
            {
                g.DrawString("未能完成操作", hFont, hb, S(48), S(128));
            }

            using (Font f = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(_theme.OnErrorContainer))
            {
                RectangleF tr = new RectangleF(S(48), S(158), errRect.Width - S(40), S(96));
                g.DrawString(_errorMessage, f, tb, tr);
            }

            int btnY = S(306);
            int btnH = S(42);
            int retryW = S(96);
            int closeW = S(76);
            int rightEdge = w - S(28);

            _rectPrimaryBtn = new Rectangle(rightEdge - retryW, btnY, retryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - closeW, btnY, closeW, btnH);

            DrawTextButton(g, _rectSecondaryBtn, "关闭", _hoveredId == "secondary");
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, "重试", _hoveredId == "primary", false);
        }

        private void DrawPrimaryFilledButton(Graphics g, Rectangle r, string text, bool hovered, bool danger)
        {
            Color fill = danger
                ? (hovered ? _theme.ErrorHover : _theme.Error)
                : (hovered ? _theme.PrimaryHover : _theme.Primary);
            Color fg = danger ? _theme.OnError : _theme.OnPrimary;

            using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
            using (SolidBrush b = new SolidBrush(fill))
            using (Font f = new Font("Microsoft YaHei UI", 9.8f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(fg))
            {
                g.FillPath(b, p);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawTonalButton(Graphics g, Rectangle r, string text, int radius, bool hovered)
        {
            Color fill = hovered ? _theme.PrimaryContainerHover : _theme.PrimaryContainer;
            using (GraphicsPath p = CreateRoundRect(r, radius))
            using (SolidBrush b = new SolidBrush(fill))
            using (Font f = new Font("Microsoft YaHei UI", 9.0f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(_theme.OnPrimaryContainer))
            {
                g.FillPath(b, p);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawTextButton(Graphics g, Rectangle r, string text, bool hovered)
        {
            if (hovered)
            {
                using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
                using (SolidBrush b = new SolidBrush(_theme.SurfaceContainerHigh))
                {
                    g.FillPath(b, p);
                }
            }
            using (Font f = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Bold, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(_theme.OnSurfaceVariant))
            {
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private Rectangle DrawMaterialCheckbox(Graphics g, int x, int y, string label, bool isChecked, bool hovered)
        {
            int boxSz = S(20);
            Rectangle box = new Rectangle(x, y, boxSz, boxSz);

            if (hovered)
            {
                Rectangle halo = new Rectangle(x - S(5), y - S(5), boxSz + S(10), boxSz + S(10));
                using (SolidBrush hb = new SolidBrush(_theme.PrimaryContainer))
                {
                    g.FillEllipse(hb, halo);
                }
            }

            using (GraphicsPath bp = CreateRoundRect(box, S(6)))
            {
                if (isChecked)
                {
                    using (SolidBrush bf = new SolidBrush(_theme.Primary))
                    {
                        g.FillPath(bf, bp);
                    }
                    using (Pen ckPen = new Pen(_theme.OnPrimary, Math.Max(1.8f, 2.0f * _dpiScale)))
                    {
                        ckPen.StartCap = LineCap.Round;
                        ckPen.EndCap = LineCap.Round;
                        ckPen.LineJoin = LineJoin.Round;
                        g.DrawLines(ckPen, new Point[]
                        {
                            new Point(box.X + S(5), box.Y + S(10)),
                            new Point(box.X + S(9), box.Y + S(14)),
                            new Point(box.X + S(15), box.Y + S(6))
                        });
                    }
                }
                else
                {
                    Color borderCol = hovered ? _theme.Primary : _theme.Outline;
                    using (Pen bPen = new Pen(borderCol, Math.Max(1.6f, 1.8f * _dpiScale)))
                    {
                        g.DrawPath(bPen, bp);
                    }
                }
            }

            using (Font f = new Font("Microsoft YaHei UI", 9.2f, FontStyle.Regular, GraphicsUnit.Point))
            using (SolidBrush tb = new SolidBrush(_theme.OnSurface))
            {
                SizeF sz = g.MeasureString(label, f);
                g.DrawString(label, f, tb, box.Right + S(8), y + (boxSz - sz.Height) / 2f);
                return new Rectangle(x - S(4), y - S(4), boxSz + S(14) + (int)sz.Width, boxSz + S(8));
            }
        }

        private string HitTestId(Point pt)
        {
            if (_rectCloseBtn.Contains(pt) && _stage != SetupStage.Working) return "close";
            if (_rectMinBtn.Contains(pt)) return "min";
            if (_rectPrimaryBtn.Contains(pt)) return "primary";
            if (_rectSecondaryBtn.Contains(pt)) return "secondary";
            if (_rectBrowseBtn.Contains(pt)) return "browse";
            if (_rectChkDesktop.Contains(pt)) return "chk_desktop";
            if (_rectChkStartMenu.Contains(pt)) return "chk_start";
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
            if (hit == null && e.Y <= S(96))
            {
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
            else if (hit == "browse")
            {
                string picked = ModernFolderBrowser.PickFolder(Handle, _installDir, "选择 PhonAcq 安装位置");
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
                _errorMessage = "安装包数据不完整，请重新下载 PhonAcq 安装程序。";
                LayoutPathTextBox();
                Invalidate();
                return;
            }

            _stage = SetupStage.Working;
            _progressPercent = 2;
            _statusMessage = _isUninstallMode ? "正在准备卸载..." : "正在准备安装...";
            LayoutPathTextBox();
            Invalidate();

            string targetDir = _installDir;
            bool mkDesktop = _optDesktopShortcut;
            bool mkStart = _optStartMenuShortcut;
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
                            _legacyMsiCodes,
                            UpdateProgressSafe);
                    }

                    if (!IsDisposed)
                    {
                        BeginInvoke((MethodInvoker)delegate
                        {
                            _stage = SetupStage.Completed;
                            LayoutPathTextBox();
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
                            LayoutPathTextBox();
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
                        InstallerEngine.PerformInstall(selfExe, payload, target, true, true, msiCodes, null);
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
